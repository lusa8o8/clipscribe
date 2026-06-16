package com.example.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranscriptionResultHolder {
    private val _latestText = MutableStateFlow("")
    val latestText: StateFlow<String> = _latestText.asStateFlow()

    private val _durationMillis = MutableStateFlow<Long?>(null)
    val durationMillis: StateFlow<Long?> = _durationMillis.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setSuccess(text: String, durationMs: Long) {
        _latestText.value = text
        _durationMillis.value = durationMs
        _errorMessage.value = null
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

    fun clear() {
        _latestText.value = ""
        _durationMillis.value = null
        _errorMessage.value = null
    }
}
