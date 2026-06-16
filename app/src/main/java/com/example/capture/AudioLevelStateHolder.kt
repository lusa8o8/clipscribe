package com.example.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioLevelStateHolder {
    private val _rmsLevel = MutableStateFlow(0.0)
    val rmsLevel: StateFlow<Double> = _rmsLevel.asStateFlow()

    fun setRmsLevel(level: Double) {
        _rmsLevel.value = level
    }

    fun reset() {
        _rmsLevel.value = 0.0
    }
}
