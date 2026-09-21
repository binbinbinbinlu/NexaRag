package com.sitelens.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {
    private val model: PhotosViewModel by viewModels()
    private fun hasPhotoAccess(): Boolean = hasLibraryAccess() &&
        checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasLibraryAccess(): Boolean = if (Build.VERSION.SDK_INT >= 33) {
        checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
    } else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private fun fullPhotoAccess() = checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            var allowed by remember { mutableStateOf(hasPhotoAccess()) }
            var full by remember { mutableStateOf(fullPhotoAccess()) }
            var scannedScope by remember { mutableStateOf<String?>(null) }
            val lifecycle = LocalLifecycleOwner.current
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        allowed = hasPhotoAccess(); full = fullPhotoAccess()
                        if (allowed && scannedScope != null) model.scan()
                    }
                }
                lifecycle.lifecycle.addObserver(observer)
                onDispose { lifecycle.lifecycle.removeObserver(observer) }
            }
            val photoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                allowed = hasPhotoAccess(); full = fullPhotoAccess()
                if (allowed) { scannedScope = state.destination?.scope; model.scan() }
                else model.message("Photo and photo-location access are required to preserve original GPS metadata. Allow them in Android Settings if the prompt no longer appears.")
            }
            val authorization = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                try {
                    val token = DriveAuthorization.readToken(result.data != null) {
                        Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data).accessToken
                    }
                    model.authorized(token)
                } catch (e: ApiException) { model.authFailed(DriveAuthorization.failure(e.statusCode))
                } catch (e: IllegalStateException) { model.authFailed(e.message ?: DriveAuthorization.NO_RESULT)
                } catch (e: Exception) { model.authFailed(DriveAuthorization.NO_RESULT) }
            }
            fun connect(action: String) {
                if (!model.beginAuthorization(action)) return
                // Browsing arbitrary existing folders and checking pre-existing files needs the Drive scope.
                val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive"))).build()
                Identity.getAuthorizationClient(this).authorize(request).addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pending = result.pendingIntent
                        if (pending == null) model.authFailed("Could not open Google authorization.")
                        else try { authorization.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                        catch (e: Exception) { model.authFailed("Google sign-in could not open. Update Google Play services and try again.") }
                    } else {
                        val token = result.accessToken
                        if (token.isNullOrEmpty()) model.authFailed("Google did not grant Drive access.") else model.authorized(token)
                    }
                }.addOnFailureListener { model.authFailed(DriveAuthorization.failure((it as? ApiException)?.statusCode)) }
            }
            LaunchedEffect(state.destination?.scope, state.busy, allowed) {
                val scope = state.destination?.scope
                if (scope != null && allowed && !state.busy && scannedScope != scope) {
                    scannedScope = scope
                    model.scan()
                }
            }
            SiteLensApp(state, allowed, full, model, ::connect) {
                photoPermissions.launch(when {
                    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                } + Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
    }
}
