package com.example.permissions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(private val context: Context) {

    private val audioHelper = AudioPermissionHelper(context)
    private val overlayHelper = OverlayPermissionHelper(context)
    private val notificationHelper = NotificationPermissionHelper(context)

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    init {
        updatePermissionState()
    }

    fun updatePermissionState() {
        val overlay = overlayHelper.hasOverlayPermission()
        val notification = notificationHelper.hasNotificationPermission()
        val audio = audioHelper.hasAudioPermission()

        _permissionState.value = PermissionState(
            overlayGranted = overlay,
            notificationGrantedOrNotRequired = notification,
            recordAudioGranted = audio
        )
    }

    fun checkAllPermissions(): Boolean {
        updatePermissionState()
        return _permissionState.value.allGranted
    }
}
