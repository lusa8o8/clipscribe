package com.example.transcription

import android.content.Context
import com.example.core.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TranscriptionController {
    @Volatile
    private var whisperEngine: WhisperEngine? = null

    suspend fun transcribePreparedAudio(context: Context): TranscriptionResult = withContext(Dispatchers.Default) {
        try {
            val preparedAudio = PreparedAudioHolder.getLatest()
            if (preparedAudio == null) {
                return@withContext TranscriptionResult.NO_PREPARED_AUDIO
            }

            if (!WhisperNativeBridge.isLibraryLoaded() && !TranscriptionStateHolder.isDebugStubEnabled()) {
                TranscriptionStateHolder.markError()
                TranscriptionResultHolder.setError("Local transcription engine is not available on this build.")
                return@withContext TranscriptionResult.ERROR
            }

            val modelPath = ModelPathResolver.resolveModelPath(context)
            if (modelPath == null) {
                TranscriptionStateHolder.markModelMissing()
                return@withContext TranscriptionResult.MODEL_MISSING
            }

            var engine = whisperEngine
            if (engine == null) {
                engine = WhisperCppEngine()
                whisperEngine = engine
            }

            if (!engine.isLoaded()) {
                TranscriptionStateHolder.markModelLoading()
                val loaded = engine.loadModel(modelPath)
                if (!loaded) {
                    TranscriptionStateHolder.markError()
                    TranscriptionResultHolder.setError("Could not load ggml model.")
                    return@withContext TranscriptionResult.ERROR
                }
            }

            TranscriptionStateHolder.markTranscribing()

            val startTime = System.currentTimeMillis()
            val transcript = engine.transcribe(preparedAudio.floatSamples, preparedAudio.sampleRate)
            val durationMs = System.currentTimeMillis() - startTime

            if (WhisperNativeBridge.isLibraryLoaded() || TranscriptionStateHolder.isDebugStubEnabled()) {
                TranscriptionResultHolder.setSuccess(transcript, durationMs)
                TranscriptionStateHolder.markSuccess()
                return@withContext TranscriptionResult.SUCCESS
            } else {
                TranscriptionStateHolder.markError()
                TranscriptionResultHolder.setError("Local transcription engine is not available on this build.")
                return@withContext TranscriptionResult.ERROR
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TranscriptionStateHolder.markError()
            TranscriptionResultHolder.setError(e.localizedMessage ?: "Unknown transcription error")
            return@withContext TranscriptionResult.ERROR
        }
    }

    fun release() {
        whisperEngine?.release()
        whisperEngine = null
    }
}
