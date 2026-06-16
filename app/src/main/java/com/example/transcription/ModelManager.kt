package com.example.transcription

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModelManager(private val context: Context) {
    private val _isModelDownloaded = MutableStateFlow(false)
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded

    fun checkAndDownloadModel() {
        // Step 1: Placeholder for setting up/checking tiny.en presence
        _isModelDownloaded.value = true
    }
}
