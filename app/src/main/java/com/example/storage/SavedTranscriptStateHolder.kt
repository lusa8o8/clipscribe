package com.example.storage

import android.content.Context
import com.example.auth.FirebaseAuthStateHolder
import com.example.core.DebugFileLog
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

object SavedTranscriptStateHolder {
    private val repository = TranscriptRepository(LocalTranscriptStore())
    private val controller = TranscriptSaveController(
        repository = repository,
        authStateProvider = { FirebaseAuthStateHolder.getCurrentState() }
    )
    private var remoteService: RemoteTranscriptService? = null

    val transcripts: StateFlow<List<TranscriptEntity>> = controller.transcripts

    suspend fun saveTranscript(
        text: String,
        sourceDurationSeconds: Double?,
        context: Context? = null
    ): SaveTranscriptResult {
        if (text.isBlank()) {
            return SaveTranscriptResult.EMPTY_TRANSCRIPT
        }

        val authState = FirebaseAuthStateHolder.getCurrentState()
        if (!authState.canPersistTranscripts) {
            return SaveTranscriptResult.AUTH_REQUIRED
        }

        val durationSeconds = (sourceDurationSeconds ?: 0.0).roundToInt().coerceAtLeast(1)
        val service = remoteTranscriptService()
        if (service == null) {
            log(context, "Transcript save using local fallback endpointConfigured=false")
            return controller.saveTranscript(text, sourceDurationSeconds)
        }

        return when (
            val result = service.saveTranscript(
                text = text,
                durationSeconds = durationSeconds,
                firebaseIdToken = FirebaseAuthStateHolder.getLatestIdToken()
            )
        ) {
            is RemoteTranscriptOutcome.Success -> {
                val saved = result.transcripts.firstOrNull()
                if (saved != null) {
                    repository.saveTranscript(saved)
                    log(context, "Remote transcript save success id=${saved.id} chars=${saved.text.length}")
                } else {
                    repository.addTranscript(text, durationSeconds)
                    log(context, "Remote transcript save success without response transcript chars=${text.length}")
                }
                SaveTranscriptResult.SUCCESS
            }
            is RemoteTranscriptOutcome.Failure -> {
                log(context, "Remote transcript save failed message=${result.message.take(180)}")
                SaveTranscriptResult.NETWORK_ERROR
            }
        }
    }

    suspend fun deleteTranscript(id: String, context: Context? = null) {
        val service = remoteTranscriptService()
        if (service != null && FirebaseAuthStateHolder.getCurrentState().canPersistTranscripts) {
            when (val result = service.deleteTranscript(
                id = id,
                firebaseIdToken = FirebaseAuthStateHolder.getLatestIdToken()
            )) {
                is RemoteTranscriptOutcome.Success -> log(context, "Remote transcript delete success id=$id")
                is RemoteTranscriptOutcome.Failure -> log(
                    context,
                    "Remote transcript delete failed id=$id message=${result.message.take(180)}"
                )
            }
        }
        controller.deleteTranscript(id)
    }

    suspend fun refreshTranscripts(context: Context? = null) {
        val service = remoteTranscriptService() ?: return
        if (!FirebaseAuthStateHolder.getCurrentState().canPersistTranscripts) {
            return
        }

        when (val result = service.listTranscripts(FirebaseAuthStateHolder.getLatestIdToken())) {
            is RemoteTranscriptOutcome.Success -> {
                repository.replaceAll(result.transcripts)
                log(context, "Remote transcript refresh success count=${result.transcripts.size}")
            }
            is RemoteTranscriptOutcome.Failure -> {
                log(context, "Remote transcript refresh failed message=${result.message.take(180)}")
                // Keep the last local snapshot visible if refresh fails.
            }
        }
    }

    suspend fun clearAll() {
        controller.clearAll()
    }

    private fun remoteTranscriptService(): RemoteTranscriptService? {
        if (!RemoteTranscriptConfig.isEnabled()) {
            return null
        }

        var service = remoteService
        if (service == null) {
            service = RemoteTranscriptService(
                endpointUrl = RemoteTranscriptConfig.endpointUrl(),
                httpClient = DefaultRemoteTranscriptHttpClient(),
                debugLog = { message, throwable ->
                    android.util.Log.i("ClipScribeDebug", message, throwable)
                }
            )
            remoteService = service
        }
        return service
    }

    private fun log(context: Context?, message: String) {
        if (context != null) {
            DebugFileLog.write(context, message)
        } else {
            android.util.Log.i("ClipScribeDebug", message)
        }
    }
}
