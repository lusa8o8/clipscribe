package com.example.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranscriptionResultHolder {
    data class FreeTierUsage(
        val dailyLimit: Int?,
        val dailyUsed: Int?,
        val dailyRemaining: Int?
    )

    private val _latestText = MutableStateFlow("")
    val latestText: StateFlow<String> = _latestText.asStateFlow()

    private val _durationMillis = MutableStateFlow<Long?>(null)
    val durationMillis: StateFlow<Long?> = _durationMillis.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _freeTierUsage = MutableStateFlow(FreeTierUsage(null, null, null))
    val freeTierUsage: StateFlow<FreeTierUsage> = _freeTierUsage.asStateFlow()

    fun setSuccess(
        text: String,
        durationMs: Long?,
        freeTierDailyLimit: Int? = null,
        freeTierDailyUsed: Int? = null,
        freeTierDailyRemaining: Int? = null
    ) {
        _latestText.value = text
        _durationMillis.value = durationMs
        _errorMessage.value = null
        _freeTierUsage.value = FreeTierUsage(
            dailyLimit = freeTierDailyLimit,
            dailyUsed = freeTierDailyUsed,
            dailyRemaining = freeTierDailyRemaining
        )
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

    fun setFreeTierUsage(
        dailyLimit: Int?,
        dailyUsed: Int?,
        dailyRemaining: Int?
    ) {
        _freeTierUsage.value = FreeTierUsage(
            dailyLimit = dailyLimit,
            dailyUsed = dailyUsed,
            dailyRemaining = dailyRemaining
        )
    }

    fun clear() {
        _latestText.value = ""
        _durationMillis.value = null
        _errorMessage.value = null
        _freeTierUsage.value = FreeTierUsage(null, null, null)
    }
}
