package com.example.core

enum class CaptureState {
    IDLE,
    RECORDING,
    PAUSED
}

enum class TranscriptionState {
    IDLE,
    PREPROCESSING,
    TRANSCRIBING,
    SUCCESS,
    ERROR
}

data class AppState(
    val isRecording: Boolean = false,
    val captureState: CaptureState = CaptureState.IDLE,
    val transcriptionState: TranscriptionState = TranscriptionState.IDLE,
    val currentBufferProgress: Float = 0f,
    val error: String? = null
)
