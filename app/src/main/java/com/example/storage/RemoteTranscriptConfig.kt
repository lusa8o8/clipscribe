package com.example.storage

import com.example.transcription.RemoteTranscriptionConfig

object RemoteTranscriptConfig {
    fun endpointUrl(): String {
        val transcriptionEndpoint = RemoteTranscriptionConfig.endpointUrl()
        if (transcriptionEndpoint.isBlank()) {
            return ""
        }
        return transcriptionEndpoint
            .substringBeforeLast("/", missingDelimiterValue = transcriptionEndpoint)
            .trimEnd('/') + "/transcripts"
    }

    fun isEnabled(): Boolean = endpointUrl().isNotBlank()
}
