package com.example.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureModeStateHolder {
    private val _state = MutableStateFlow(CaptureModeState.OFF)
    val state: StateFlow<CaptureModeState> = _state.asStateFlow()

    fun markStarting() {
        _state.value = CaptureModeState.STARTING
    }

    fun markActive() {
        _state.value = CaptureModeState.ACTIVE
    }

    fun markStopping() {
        _state.value = CaptureModeState.STOPPING
    }

    fun markOff() {
        _state.value = CaptureModeState.OFF
    }

    fun markError() {
        _state.value = CaptureModeState.ERROR
    }

    fun currentState(): CaptureModeState {
        return _state.value
    }
}
