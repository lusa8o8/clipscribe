package com.example.transcription

import com.example.BuildConfig

object RemoteTranscriptionConfig {
    fun endpointUrl(): String = BuildConfig.TRANSCRIPTION_ENDPOINT_URL.trim()

    fun isEnabled(): Boolean = endpointUrl().isNotBlank()
}
