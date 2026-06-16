package com.example.ui.result

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranscriptResultStateHolder {
    private val _state = MutableStateFlow(TranscriptResultState())
    val state: StateFlow<TranscriptResultState> = _state.asStateFlow()

    fun showSuccess(text: String, durationMillis: Long?, sourceDurationSeconds: Double?) {
        _state.value = TranscriptResultState(
            isVisible = true,
            text = text,
            durationMillis = durationMillis,
            sourceDurationSeconds = sourceDurationSeconds,
            errorMessage = null
        )
    }

    fun showError(message: String) {
        _state.value = TranscriptResultState(
            isVisible = true,
            text = "",
            durationMillis = null,
            sourceDurationSeconds = null,
            errorMessage = message
        )
    }

    fun dismiss() {
        _state.value = _state.value.copy(isVisible = false)
    }

    fun clear() {
        _state.value = TranscriptResultState()
    }
}
