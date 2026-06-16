package com.example.storage

import com.example.auth.FirebaseAuthStateHolder
import kotlinx.coroutines.flow.StateFlow

object SavedTranscriptStateHolder {
    private val controller = TranscriptSaveController(
        repository = TranscriptRepository(LocalTranscriptStore()),
        authStateProvider = { FirebaseAuthStateHolder.getCurrentState() }
    )

    val transcripts: StateFlow<List<TranscriptEntity>> = controller.transcripts

    suspend fun saveTranscript(text: String, sourceDurationSeconds: Double?): SaveTranscriptResult {
        return controller.saveTranscript(text, sourceDurationSeconds)
    }

    suspend fun deleteTranscript(id: String) {
        controller.deleteTranscript(id)
    }

    suspend fun clearAll() {
        controller.clearAll()
    }
}
