package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeveloperModeStateHolder {
    private val _isDeveloperModeEnabled = MutableStateFlow(false)
    val isDeveloperModeEnabled: StateFlow<Boolean> = _isDeveloperModeEnabled.asStateFlow()

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _isDeveloperModeEnabled.value = enabled
    }

    fun toggleDeveloperMode() {
        _isDeveloperModeEnabled.value = !_isDeveloperModeEnabled.value
    }
}
