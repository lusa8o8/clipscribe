package com.example.capture

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CaptureController(private val context: Context) {
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val rollingBuffer = RollingAudioBuffer(
        sampleRate = com.example.core.Constants.TARGET_SAMPLE_RATE,
        maxDurationSeconds = com.example.core.Constants.DEFAULT_BUFFER_SECONDS
    )

    fun startCapture(mediaProjectionIntent: android.content.Intent) {
        _isCapturing.value = true
    }

    fun stopCapture() {
        _isCapturing.value = false
    }

    fun getCapturedData(): ShortArray {
        return rollingBuffer.snapshot()
    }
}
