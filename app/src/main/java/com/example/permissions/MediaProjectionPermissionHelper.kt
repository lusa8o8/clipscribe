package com.example.permissions

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

class MediaProjectionPermissionHelper(private val context: Context) {
    private val mediaProjectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    fun createCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }
}
