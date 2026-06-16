package com.example.transcription

enum class TranscriptionResult {
    SUCCESS,
    NO_PREPARED_AUDIO,
    MODEL_MISSING,
    AUTH_REQUIRED,
    QUOTA_EXCEEDED,
    ERROR
}
