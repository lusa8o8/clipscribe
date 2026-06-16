package com.example.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class AudioPermissionHelper(private val context: Context) {
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}
