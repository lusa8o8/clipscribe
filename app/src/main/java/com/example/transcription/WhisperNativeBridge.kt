package com.example.transcription

import android.util.Log

object WhisperNativeBridge {
    private const val TAG = "WhisperNativeBridge"
    private var isLibLoaded = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            isLibLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library whisper_jni not loaded: ${e.message}. Fallback simulations will be used.")
        }
    }

    fun isLibraryLoaded(): Boolean = isLibLoaded

    external fun initContext(modelPath: String): Long
    external fun transcribe(contextPtr: Long, samples: FloatArray, sampleRate: Int): String
    external fun releaseContext(contextPtr: Long)
}
