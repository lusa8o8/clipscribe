package com.example.transcription

import android.content.Context
import com.example.auth.FirebaseAuthStateHolder
import com.example.core.DebugFileLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TranscriptionController {
    @Volatile
    private var whisperEngine: WhisperEngine? = null
    @Volatile
    private var remoteService: RemoteTranscriptionService? = null

    fun isTranscriptionAvailable(): Boolean {
        return RemoteTranscriptionConfig.isEnabled() ||
            WhisperNativeBridge.isLibraryLoaded() ||
            TranscriptionStateHolder.isDebugStubEnabled()
    }

    suspend fun transcribePreparedAudio(context: Context): TranscriptionResult = withContext(Dispatchers.Default) {
        try {
            val preparedAudio = PreparedAudioHolder.getLatest()
            if (preparedAudio == null) {
                return@withContext TranscriptionResult.NO_PREPARED_AUDIO
            }

            if (RemoteTranscriptionConfig.isEnabled()) {
                TranscriptionStateHolder.markTranscribing()
                var service = remoteService
                if (service == null) {
                    service = RemoteTranscriptionService(
                        endpointUrl = RemoteTranscriptionConfig.endpointUrl(),
                        httpClient = DefaultRemoteTranscriptionHttpClient(),
                        debugLog = { message, throwable ->
                            DebugFileLog.write(context, message, throwable)
                        }
                    )
                    remoteService = service
                }

                return@withContext when (
                    val remoteResult = service.transcribe(
                        preparedAudio = preparedAudio,
                        firebaseIdToken = FirebaseAuthStateHolder.getLatestIdToken()
                    )
                ) {
                    is RemoteTranscriptionOutcome.Success -> {
                        TranscriptionResultHolder.setSuccess(
                            text = remoteResult.value.text,
                            durationMs = remoteResult.value.durationMillis,
                            freeTierDailyLimit = remoteResult.value.freeTierDailyLimit,
                            freeTierDailyUsed = remoteResult.value.freeTierDailyUsed,
                            freeTierDailyRemaining = remoteResult.value.freeTierDailyRemaining
                        )
                        TranscriptionStateHolder.markSuccess()
                        TranscriptionResult.SUCCESS
                    }

                    is RemoteTranscriptionOutcome.Failure -> {
                        val failure = remoteResult.value
                        DebugFileLog.write(
                            context,
                            "Remote transcription mapped failure code=${failure.code} message=${failure.message.take(180)}"
                        )
                        TranscriptionResultHolder.setError(failure.message)
                        TranscriptionStateHolder.markError()
                        if (failure.code == RemoteTranscriptionFailureCode.AUTH_TOKEN_MISSING ||
                            failure.code == RemoteTranscriptionFailureCode.UNAUTHORIZED
                        ) {
                            TranscriptionResult.AUTH_REQUIRED
                        } else if (failure.code == RemoteTranscriptionFailureCode.QUOTA_EXCEEDED) {
                            TranscriptionResult.QUOTA_EXCEEDED
                        } else {
                            TranscriptionResult.ERROR
                        }
                    }
                }
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
