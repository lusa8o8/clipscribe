package com.example.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocalTranscriptStore {
    private val _transcripts = MutableStateFlow<List<TranscriptEntity>>(emptyList())
    val transcripts: StateFlow<List<TranscriptEntity>> = _transcripts

    fun saveTranscript(transcript: TranscriptEntity) {
        val currentList = _transcripts.value.toMutableList()
        currentList.add(0, transcript) // Insert at beginning
        if (currentList.size > 10) { // MAX_SAVED_TRANSCRIPTS = 10
            currentList.removeAt(currentList.lastIndex)
        }
        _transcripts.value = currentList
    }

    fun deleteTranscript(id: String) {
        _transcripts.value = _transcripts.value.filter { it.id != id }
    }

    fun clearAll() {
        _transcripts.value = emptyList()
    }
}
