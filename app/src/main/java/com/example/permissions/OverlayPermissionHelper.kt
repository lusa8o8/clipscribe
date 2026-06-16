package com.example.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class OverlayPermissionHelper(private val context: Context) {
    fun hasOverlayPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context)
        }
        return true
    }

    fun createOverlayPermissionIntent(): Intent? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }
        return null
    }
}
