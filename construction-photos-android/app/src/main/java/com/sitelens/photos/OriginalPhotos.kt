package com.sitelens.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore

/** Never fall back to a redacted photo when original metadata is unavailable. */
object OriginalPhotos {
    fun requirePermission(context: Context) {
        check(context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            "Allow photo location access to preserve GPS metadata, then scan again. Uploads are blocked without it."
        }
    }
    fun uri(context: Context, source: Uri): Uri {
        requirePermission(context)
        return MediaStore.setRequireOriginal(source)
    }
}
