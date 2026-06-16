package com.example.permissions

data class PermissionState(
    val overlayGranted: Boolean = false,
    val notificationGrantedOrNotRequired: Boolean = false,
    val recordAudioGranted: Boolean = false
) {
    val allGranted: Boolean
        get() = overlayGranted && notificationGrantedOrNotRequired && recordAudioGranted
}
