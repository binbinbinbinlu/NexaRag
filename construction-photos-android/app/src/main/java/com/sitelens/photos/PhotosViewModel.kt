package com.sitelens.photos

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScreenState(
    val destination: Destination? = null, val photos: List<Photo> = emptyList(),
    val busy: Boolean = false, val progress: String = "", val message: String? = null,
    val picking: Boolean = false, val folders: List<Folder> = emptyList(),
    val path: List<Folder> = emptyList(), val shared: Boolean = false,
    val accountName: String = "", val synced: Boolean = false,
    val dateRange: PhotoDateRange = DatePreset.LAST_WEEK.range(), val conflict: UploadConflict? = null
) {
    val datedPhotos: List<Photo> get() = photos.filter { dateRange.contains(it) }
    val uploadSelection: List<Photo> get() = datedPhotos.filter { it.selected }
}

class PhotosViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("destination", 0)
    private val store = PhotoStore(application)
    private val scanner = PhotoScanner(application, store)
    private val mutable = MutableStateFlow(ScreenState(destination = savedDestination()))
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private val choiceWrites = mutableListOf<Job>()
    private var drive: DriveClient? = null
    private var browsingAccount: Pair<String, String>? = null
    private var uploadBatch: List<Photo> = emptyList()
    private var conflictAnswer: CompletableDeferred<ConflictDecision>? = null
    var pendingAction: String = "browse"
    private fun savedDestination(): Destination? {
        val id = prefs.getString("folder", null) ?: return null
        return Destination(prefs.getString("account", "")!!, prefs.getString("email", "")!!, Folder(id, prefs.getString("name", "Drive folder")!!))
    }
    fun message(text: String?) { mutable.update { it.copy(message = text) } }
    fun setDateRange(preset: DatePreset) {
        setDateRange(preset.range())
    }
    fun setDateRange(range: PhotoDateRange) {
        if (state.value.busy) return
        mutable.update { it.copy(dateRange = range, photos = emptyList(), synced = false) }
        scan()
    }
    fun answerConflict(decision: ConflictDecision) { conflictAnswer?.complete(decision) }
    private suspend fun askConflict(photo: Photo, matches: List<RemotePhoto>): ConflictDecision {
        val answer = CompletableDeferred<ConflictDecision>()
        conflictAnswer = answer
        mutable.update { it.copy(conflict = UploadConflict(photo, matches), progress = "Waiting for your replacement choice…") }
        return try { answer.await() } finally {
            conflictAnswer = null
            mutable.update { it.copy(conflict = null) }
        }
    }
    fun beginAuthorization(action: String): Boolean {
        if (state.value.busy) return false
        if (action == "upload") uploadBatch = state.value.uploadSelection.toList()
        pendingAction = action
        mutable.update { it.copy(busy = true, progress = "Connecting to Google Drive…", message = null) }
        return true
    }
    fun authFailed(text: String) { mutable.update { it.copy(busy = false, progress = "", message = text) } }
    private fun work(block: suspend () -> Unit) {
        job = viewModelScope.launch {
            mutable.update { it.copy(busy = true, message = null) }
            try {
                choiceWrites.toList().joinAll()
                choiceWrites.removeAll { it.isCompleted }
                withContext(Dispatchers.IO) { block() }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "Something went wrong. Please try again.") }
            finally { mutable.update { it.copy(busy = false, progress = "") } }
        }
    }
    fun authorized(token: String) {
        val action = pendingAction
        drive = DriveClient(token)
        work {
            val client = drive!!
            val account = client.account()
            if (action == "browse") {
                browsingAccount = account
                val root = client.folder("root")
                val folders = client.folders(root.id)
                mutable.update { it.copy(picking = true, accountName = account.second, path = listOf(root), folders = folders, shared = false) }
            } else {
                val destination = state.value.destination ?: error("Choose a Drive folder first.")
                check(account.first == destination.accountId) { "Connected to a different Google account. Change the destination folder to use this account." }
                client.folder(destination.folder.id)
                sync(client, destination)
                if (action == "upload") upload(client, destination, uploadBatch)
            }
        }
    }
    fun browse(folder: Folder? = null, up: Boolean = false, shared: Boolean = false) {
        if (state.value.busy) return
        work {
            val client = drive ?: error("Reconnect to Drive.")
            val path = when {
                shared -> emptyList()
                up -> state.value.path.dropLast(1)
                folder != null -> state.value.path + folder
                else -> listOf(client.folder("root"))
            }
            val listing = client.folders(path.lastOrNull()?.id)
            val inShared = if (shared) true else if (up || folder != null) state.value.shared else false
            mutable.update { it.copy(path = path, folders = listing, shared = inShared) }
        }
    }
    fun cancelPicker() { if (!state.value.busy) mutable.update { it.copy(picking = false) } }
    fun chooseFolder() {
        val folder = state.value.path.lastOrNull() ?: return
        if (state.value.busy) return
        work {
            val valid = drive!!.folder(folder.id)
            val account = browsingAccount ?: error("Reconnect to Drive.")
            val destination = Destination(account.first, account.second, valid)
            check(prefs.edit().putString("folder", valid.id).putString("name", valid.name).putString("account", account.first).putString("email", account.second).commit()) { "Could not save destination." }
            mutable.update { it.copy(destination = destination, picking = false, photos = emptyList(), synced = false, message = "Folder saved. Allow photo access, then scan your photos.") }
        }
    }
    fun scan() {
        val destination = state.value.destination ?: return
        if (state.value.busy) return
        val range = state.value.dateRange
        work {
            val photos = scanner.scan(destination, range) { progress -> mutable.update { it.copy(progress = progress) } }
            mutable.update { it.copy(photos = photos, synced = false, message = "Scan complete. Review suggestions, then check Drive or upload.") }
            drive?.let { client ->
                try {
                    if (client.account().first == destination.accountId) sync(client, destination)
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    message("Photos scanned. Drive status could not refresh; tap Check Drive to reconnect. Saved upload tags are shown.")
                }
            }
        }
    }
    fun select(key: String, selected: Boolean) {
        if (state.value.busy) return
        val photo = state.value.photos.find { it.key == key } ?: return
        if (photo.uploaded) {
            mutable.update { it.copy(photos = it.photos.map { p -> if (p.key == key) p.copy(reupload = selected) else p }) }
            return
        }
        val changed = photo.copy(override = selected)
        mutable.update { it.copy(photos = it.photos.map { p -> if (p.key == key) changed else p }) }
        choiceWrites += viewModelScope.launch(Dispatchers.IO) {
            try { store.save(changed) } catch (e: Exception) { message("Could not save your selection. Please try again.") }
        }
    }
    private fun sync(client: DriveClient, destination: Destination) {
        mutable.update { it.copy(progress = "Checking photos already in this Drive folder…") }
        val hashes = client.hashes(destination.folder.id)
        val updated = state.value.photos.map { photo ->
            val id = hashes[photo.hash]
            if (id != null) store.record(destination.scope, photo.hash, id, true)
            else if (store.receipt(destination.scope, photo.hash)?.second == true) store.forget(destination.scope, photo.hash)
            photo.copy(uploaded = id != null)
        }
        mutable.update { it.copy(photos = updated, synced = true, message = "Drive checked. ${updated.count { p -> p.uploaded }} photos already uploaded.") }
    }
    private suspend fun upload(client: DriveClient, destination: Destination, selected: List<Photo>) {
        OriginalPhotos.requirePermission(getApplication())
        var completed = 0
        var failed = 0
        var skipped = 0
        for ((index, photo) in selected.withIndex()) {
            mutable.update { it.copy(progress = "Uploading ${index + 1} of ${selected.size}…") }
            try {
                check(photo.hash.isNotEmpty()) { "Photo cannot be read. Grant access and scan again." }
                // Check again for each item, including same-name collisions within this batch.
                val matches = UploadConflicts.matches(photo, client.contents(destination.folder.id))
                val target = if (matches.isEmpty()) null else when (val decision = askConflict(photo, matches)) {
                    ConflictDecision.Cancel -> {
                        message("Upload canceled. $completed uploaded, $skipped skipped, $failed failed. Remaining photos were not uploaded.")
                        return
                    }
                    ConflictDecision.Skip -> { skipped++; continue }
                    is ConflictDecision.Replace -> matches.find { it.id == decision.id } ?: error("Choose an existing file to replace.")
                }
                val id = run {
                    val resolver = getApplication<Application>().contentResolver
                    val uri = OriginalPhotos.uri(getApplication(), Uri.parse(photo.uri))
                    val digest = java.security.MessageDigest.getInstance("MD5")
                    resolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    } ?: error("Photo is no longer accessible. Allow access and scan again.")
                    check(digest.digest().joinToString("") { "%02x".format(it) } == photo.hash) { "Photo changed since the scan. Tap Scan before uploading." }
                    val receipt = store.receipt(destination.scope, photo.hash)
                    var pendingId = receipt?.takeIf { !it.second }?.first
                    if (pendingId != null) {
                        val existing = client.find(pendingId)
                        if (existing != null && !client.verified(existing, photo.hash, destination.folder.id)) {
                            store.forget(destination.scope, photo.hash)
                            pendingId = null
                        }
                    }
                    val body = OriginalPhotoBody(photo.mime,
                        length = { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L },
                        openOriginal = {
                            OriginalPhotos.requirePermission(getApplication())
                            resolver.openInputStream(uri) ?: error("Original photo is unavailable. Upload stopped to preserve location metadata.")
                        }
                    )
                    if (target != null) client.replace(target, photo, destination.folder.id, body)
                    else {
                        val uploadId = pendingId ?: client.generateId().also { store.record(destination.scope, photo.hash, it, false) }
                        client.upload(uploadId, photo, destination.folder.id, body)
                    }
                }
                store.record(destination.scope, photo.hash, id, true)
                completed++
                mutable.update { it.copy(photos = it.photos.map { p -> if (p.hash == photo.hash) p.copy(uploaded = true, reupload = false, error = null) else p }) }
                // Replacing a different image also invalidates its old green tag.
                if (target != null && target.hash != photo.hash) {
                    store.forget(destination.scope, target.hash)
                    mutable.update { it.copy(synced = false, photos = it.photos.map { p -> if (p.hash == target.hash) p.copy(uploaded = false, reupload = false) else p }) }
                    try { sync(client, destination) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { mutable.update { it.copy(synced = false) } }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                failed++
                mutable.update { it.copy(photos = it.photos.map { p -> if (p.key == photo.key) p.copy(error = e.message ?: "Upload failed. Retry.") else p }) }
                if (e is DriveException && (e.status == 401 || e.status == 403 || e.status == 429)) {
                    message("$completed uploaded. ${e.message} Remaining selections are saved.")
                    return
                }
            }
        }
        message("$completed uploaded, $skipped skipped, $failed failed." + if (failed > 0) " Tap Upload to retry selected photos." else "")
    }
}
