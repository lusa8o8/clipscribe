package com.example.transcription

enum class TranscriptionState {
    IDLE,
    MODEL_MISSING,
    MODEL_LOADING,
    READY,
    TRANSCRIBING,
    SUCCESS,
    ERROR
}
