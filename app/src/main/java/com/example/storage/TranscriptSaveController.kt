package com.example.storage

import com.example.auth.AuthState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

enum class SaveTranscriptResult {
    SUCCESS,
    AUTH_REQUIRED,
    EMPTY_TRANSCRIPT,
    NETWORK_ERROR
}

class TranscriptSaveController(
    private val repository: TranscriptRepository,
    private val authStateProvider: () -> AuthState
) {
    val transcripts: StateFlow<List<TranscriptEntity>> = repository.allTranscripts

    suspend fun saveTranscript(text: String, sourceDurationSeconds: Double?): SaveTranscriptResult {
        if (text.isBlank()) {
            return SaveTranscriptResult.EMPTY_TRANSCRIPT
        }

        if (!authStateProvider().canPersistTranscripts) {
            return SaveTranscriptResult.AUTH_REQUIRED
        }

        val durationSeconds = (sourceDurationSeconds ?: 0.0).roundToInt().coerceAtLeast(1)
        repository.addTranscript(text = text, durationSeconds = durationSeconds)
        return SaveTranscriptResult.SUCCESS
    }

    suspend fun deleteTranscript(id: String) {
        repository.removeTranscript(id)
    }

    suspend fun clearAll() {
        repository.clear()
    }
}
