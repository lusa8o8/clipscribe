package com.example.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioCaptureStateHolder {
    private val _captureState = MutableStateFlow(AudioCaptureState.NOT_BUILT)
    val captureState: StateFlow<AudioCaptureState> = _captureState.asStateFlow()

    fun markInitializing() {
        _captureState.value = AudioCaptureState.INITIALIZING
    }

    fun markReady() {
        _captureState.value = AudioCaptureState.READY
    }

    fun markCapturing() {
        _captureState.value = AudioCaptureState.CAPTURING
    }

    fun markAudioDetected() {
        _captureState.value = AudioCaptureState.AUDIO_DETECTED
    }

    fun markNoAudioDetected() {
        _captureState.value = AudioCaptureState.NO_AUDIO_DETECTED
    }

    fun markBlockedOrUnsupported() {
        _captureState.value = AudioCaptureState.BLOCKED_OR_UNSUPPORTED
    }

    fun markError() {
        _captureState.value = AudioCaptureState.ERROR
    }

    fun markStopped() {
        _captureState.value = AudioCaptureState.STOPPED
    }

    fun reset() {
        _captureState.value = AudioCaptureState.NOT_BUILT
    }
}
