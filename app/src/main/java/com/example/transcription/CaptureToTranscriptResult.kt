package com.example.transcription

enum class CaptureToTranscriptResult {
    SUCCESS,
    NO_CAPTURE_SERVICE,
    NO_BUFFER_AVAILABLE,
    AUDIO_TOO_SHORT,
    NO_PREPARED_AUDIO,
    MODEL_MISSING,
    AUTH_REQUIRED,
    ENGINE_NOT_AVAILABLE,
    TRANSCRIPTION_ERROR,
    ERROR
}
