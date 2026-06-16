package com.example.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureServiceStateHolder {
    private val _serviceState = MutableStateFlow(CaptureServiceState.OFF)
    val serviceState: StateFlow<CaptureServiceState> = _serviceState.asStateFlow()

    fun currentState(): CaptureServiceState {
        return _serviceState.value
    }

    fun markStarting() {
        _serviceState.value = CaptureServiceState.STARTING
    }

    fun markActive() {
        _serviceState.value = CaptureServiceState.ACTIVE
    }

    fun markStopped() {
        _serviceState.value = CaptureServiceState.STOPPED
    }

    fun markError() {
        _serviceState.value = CaptureServiceState.ERROR
    }

    fun reset() {
        _serviceState.value = CaptureServiceState.OFF
    }
}
