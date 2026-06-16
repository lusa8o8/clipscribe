package com.example.transcription

interface WhisperEngine {
    suspend fun loadModel(modelPath: String): Boolean
    suspend fun transcribe(samples: FloatArray, sampleRate: Int): String
    fun isLoaded(): Boolean
    fun release()
}
