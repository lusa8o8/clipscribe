package com.example.ui.result

data class TranscriptResultState(
    val isVisible: Boolean = false,
    val text: String = "",
    val durationMillis: Long? = null,
    val sourceDurationSeconds: Double? = null,
    val errorMessage: String? = null
)
