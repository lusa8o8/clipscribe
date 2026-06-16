package com.example.transcription

import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TranscriptionStateHolder {
    private val _state = MutableStateFlow(TranscriptionState.IDLE)
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    private val _engineMode = MutableStateFlow(TranscriptionEngineMode.NOT_AVAILABLE)
    val engineMode: StateFlow<TranscriptionEngineMode> = _engineMode.asStateFlow()

    private var isDebugStubEnabledForTests = BuildConfig.DEBUG

    init {
        updateEngineMode()
    }

    fun enableDebugStubForTesting() {
        isDebugStubEnabledForTests = true
        updateEngineMode()
    }

    fun isDebugStubEnabled(): Boolean = BuildConfig.DEBUG || isDebugStubEnabledForTests

    fun updateEngineMode() {
        _engineMode.value = when {
            RemoteTranscriptionConfig.isEnabled() -> TranscriptionEngineMode.REMOTE_ENDPOINT
            WhisperNativeBridge.isLibraryLoaded() -> TranscriptionEngineMode.NATIVE_WHISPER
            isDebugStubEnabled() -> TranscriptionEngineMode.DEBUG_STUB
            else -> TranscriptionEngineMode.NOT_AVAILABLE
        }
    }

    fun markModelMissing() {
        _state.value = TranscriptionState.MODEL_MISSING
    }

    fun markModelLoading() {
        _state.value = TranscriptionState.MODEL_LOADING
    }

    fun markReady() {
        _state.value = TranscriptionState.READY
    }

    fun markTranscribing() {
        _state.value = TranscriptionState.TRANSCRIBING
    }

    fun markSuccess() {
        _state.value = TranscriptionState.SUCCESS
    }

    fun markError() {
        _state.value = TranscriptionState.ERROR
    }

    fun reset() {
        _state.value = TranscriptionState.IDLE
        isDebugStubEnabledForTests = false
        updateEngineMode()
    }
}
