package com.example.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class WhisperCppEngine : WhisperEngine {
    private var contextPtr: Long = 0L

    override suspend fun loadModel(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!WhisperNativeBridge.isLibraryLoaded()) {
            if (TranscriptionStateHolder.isDebugStubEnabled()) {
                Log.w("WhisperCppEngine", "Native library not loaded. Simulating model loading for tests: $modelPath")
                contextPtr = 9999L // simulated context pointer for DEBUG_STUB in tests
                return@withContext true
            } else {
                Log.e("WhisperCppEngine", "Native library whisper_jni not loaded and DEBUG_STUB not enabled.")
                return@withContext false
            }
        }

        try {
            val ptr = WhisperNativeBridge.initContext(modelPath)
            if (ptr != 0L) {
                contextPtr = ptr
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun transcribe(samples: FloatArray, sampleRate: Int): String = withContext(Dispatchers.Default) {
        if (!isLoaded()) {
            return@withContext ""
        }

        if (!WhisperNativeBridge.isLibraryLoaded()) {
            if (TranscriptionStateHolder.isDebugStubEnabled()) {
                val durationSec = samples.size.toDouble() / sampleRate
                return@withContext "[DEBUG-STUB] Captured ${String.format(java.util.Locale.US, "%.1f", durationSec)} seconds of audio. This is a local mock spike transcript."
            } else {
                return@withContext "Error: Native library not loaded"
            }
        }

        try {
            WhisperNativeBridge.transcribe(contextPtr, samples, sampleRate)
        } catch (e: Exception) {
            e.printStackTrace()
            "Error during native transcription: ${e.message}"
        }
    }

    override fun isLoaded(): Boolean {
        return contextPtr != 0L
    }

    override fun release() {
        if (isLoaded()) {
            if (WhisperNativeBridge.isLibraryLoaded() && contextPtr != 9999L) {
                try {
                    WhisperNativeBridge.releaseContext(contextPtr)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            contextPtr = 0L
        }
    }
}
