package com.example.storage

import kotlinx.coroutines.flow.StateFlow

class TranscriptRepository(private val store: LocalTranscriptStore) {
    val allTranscripts: StateFlow<List<TranscriptEntity>> = store.transcripts

    suspend fun addTranscript(text: String, durationSeconds: Int) {
        val entity = TranscriptEntity(text = text, durationSeconds = durationSeconds)
        store.saveTranscript(entity)
    }

    suspend fun saveTranscript(entity: TranscriptEntity) {
        store.saveTranscript(entity)
    }

    suspend fun replaceAll(transcripts: List<TranscriptEntity>) {
        store.replaceAll(transcripts)
    }

    suspend fun removeTranscript(id: String) {
        store.deleteTranscript(id)
    }

    suspend fun clear() {
        store.clearAll()
    }
}
