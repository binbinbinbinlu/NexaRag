package com.sitelens.photos

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class PhotoScanner(private val context: Context, private val store: PhotoStore) {
    suspend fun scan(destination: Destination, range: PhotoDateRange, progress: (String) -> Unit): List<Photo> {
        OriginalPhotos.requirePermission(context)
        val result = mutableListOf<Photo>()
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        try {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
            val query = PhotoScanQuery.forRange(range)
            context.contentResolver.query(collection, columns, query.selection, query.args.toTypedArray(), "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    progress("Scanning ${cursor.position + 1} of ${cursor.count} photos…")
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    val key = "$uri:${cursor.getLong(3)}:${cursor.getLong(4)}"
                    val cached = store.cached(key)
                    var photo = (cached ?: Photo(key, "", "", "", "", false, "Not analyzed"))
                        .copy(uri = uri.toString(), name = cursor.getString(1) ?: "Photo.jpg", mime = cursor.getString(2) ?: "image/jpeg",
                            takenAtMillis = cursor.getLong(5).takeIf { it > 0 } ?: cursor.getLong(6).takeIf { it > 0 }?.times(1000) ?: cursor.getLong(4).takeIf { it > 0 }?.times(1000))
                    try {
                        if (cached == null || cached.hash.isEmpty() || cached.reason == "Needs review" || cached.reason == "Not analyzed") {
                            val digest = MessageDigest.getInstance("MD5")
                            context.contentResolver.openInputStream(OriginalPhotos.uri(context, uri))?.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    digest.update(buffer, 0, count)
                                }
                            } ?: error("Photo is no longer accessible")
                            photo = photo.copy(hash = digest.digest().joinToString("") { "%02x".format(it) })
                            val labels = labeler.process(InputImage.fromFilePath(context, uri)).await()
                            val suggestion = ConstructionClassifier.classify(labels.map { LabelScore(it.text, it.confidence) })
                            photo = photo.copy(suggested = suggestion.selected, reason = suggestion.reason)
                            store.save(photo)
                        }
                        photo = photo.copy(uploaded = store.receipt(destination.scope, photo.hash)?.second == true)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                    } catch (e: Exception) {
                        // Keep unreadable/unsupported photos visible for manual review; retry classification next scan.
                        photo = photo.copy(error = "Could not analyze. You can select it manually if readable.", reason = "Needs review")
                    }
                    result += photo
                }
            }
        } finally { labeler.close() }
        return result
    }
}
