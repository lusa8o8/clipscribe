package com.example.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioPreparationStateHolder {
    private val _preparationState = MutableStateFlow(AudioPreparationState.IDLE)
    val preparationState: StateFlow<AudioPreparationState> = _preparationState.asStateFlow()

    private val _preparedDurationSeconds = MutableStateFlow(0.0)
    val preparedDurationSeconds: StateFlow<Double> = _preparedDurationSeconds.asStateFlow()

    private val _preparedSampleCount = MutableStateFlow(0)
    val preparedSampleCount: StateFlow<Int> = _preparedSampleCount.asStateFlow()

    fun markPreparing() {
        _preparationState.value = AudioPreparationState.PREPARING
    }

    fun markReady(durationSeconds: Double, sampleCount: Int) {
        _preparationState.value = AudioPreparationState.READY
        _preparedDurationSeconds.value = durationSeconds
        _preparedSampleCount.value = sampleCount
    }

    fun markNoFrozenBuffer() {
        _preparationState.value = AudioPreparationState.NO_FROZEN_BUFFER
        _preparedDurationSeconds.value = 0.0
        _preparedSampleCount.value = 0
    }

    fun markError() {
        _preparationState.value = AudioPreparationState.ERROR
    }

    fun reset() {
        _preparationState.value = AudioPreparationState.IDLE
        _preparedDurationSeconds.value = 0.0
        _preparedSampleCount.value = 0
    }
}
