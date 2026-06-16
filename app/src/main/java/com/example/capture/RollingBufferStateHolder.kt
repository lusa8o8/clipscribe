package com.example.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RollingBufferStateHolder {
    private val _bufferState = MutableStateFlow(RollingBufferState.EMPTY)
    val bufferState: StateFlow<RollingBufferState> = _bufferState.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0.0)
    val durationSeconds: StateFlow<Double> = _durationSeconds.asStateFlow()

    private val _lastFrozenSampleCount = MutableStateFlow(0)
    val lastFrozenSampleCount: StateFlow<Int> = _lastFrozenSampleCount.asStateFlow()

    fun updateDuration(seconds: Double) {
        _durationSeconds.value = seconds
        val currentState = _bufferState.value
        if (currentState != RollingBufferState.FROZEN && currentState != RollingBufferState.ERROR) {
            if (seconds == 0.0) {
                _bufferState.value = RollingBufferState.EMPTY
            } else if (seconds >= 45.0) {
                _bufferState.value = RollingBufferState.READY
            } else {
                _bufferState.value = RollingBufferState.FILLING
            }
        }
    }

    fun markFrozen(sampleCount: Int) {
        _bufferState.value = RollingBufferState.FROZEN
        _lastFrozenSampleCount.value = sampleCount
    }

    fun markError() {
        _bufferState.value = RollingBufferState.ERROR
    }

    fun clear() {
        _bufferState.value = RollingBufferState.CLEARED
        _durationSeconds.value = 0.0
        _lastFrozenSampleCount.value = 0
    }
}
