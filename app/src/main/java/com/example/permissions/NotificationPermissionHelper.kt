package com.example.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class NotificationPermissionHelper(private val context: Context) {
    fun hasNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
