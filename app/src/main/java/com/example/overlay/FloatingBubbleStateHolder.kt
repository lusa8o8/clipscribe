package com.example.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FloatingBubbleStateHolder {
    @Volatile
    var isRunning: Boolean = false
        set(value) {
            field = value
            _isRunningFlow.value = value
        }

    private val _isRunningFlow = MutableStateFlow(false)
    val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()
}
