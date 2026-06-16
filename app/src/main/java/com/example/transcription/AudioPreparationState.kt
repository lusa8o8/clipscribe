package com.example.transcription

enum class AudioPreparationState {
    IDLE,
    NO_FROZEN_BUFFER,
    PREPARING,
    READY,
    ERROR
}
