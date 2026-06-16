package com.example.capture

enum class AudioCaptureState {
    NOT_BUILT,
    INITIALIZING,
    READY,
    CAPTURING,
    AUDIO_DETECTED,
    NO_AUDIO_DETECTED,
    BLOCKED_OR_UNSUPPORTED,
    ERROR,
    STOPPED
}
