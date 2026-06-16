package com.example.transcription

data class RemoteTranscriptionSuccess(
    val text: String,
    val durationMillis: Long?,
    val freeTierDailyLimit: Int?,
    val freeTierDailyUsed: Int?,
    val freeTierDailyRemaining: Int?
)

enum class RemoteTranscriptionFailureCode {
    ENDPOINT_NOT_CONFIGURED,
    AUTH_TOKEN_MISSING,
    PREPARED_AUDIO_MISSING,
    WAV_BYTES_MISSING,
    UNAUTHORIZED,
    QUOTA_EXCEEDED,
    UPSTREAM_ERROR,
    INVALID_RESPONSE,
    NETWORK_ERROR
}

data class RemoteTranscriptionFailure(
    val code: RemoteTranscriptionFailureCode,
    val message: String
)

sealed interface RemoteTranscriptionOutcome {
    data class Success(val value: RemoteTranscriptionSuccess) : RemoteTranscriptionOutcome
    data class Failure(val value: RemoteTranscriptionFailure) : RemoteTranscriptionOutcome
}

class RemoteTranscriptionService(
    private val endpointUrl: String,
    private val httpClient: RemoteTranscriptionHttpClient,
    private val debugLog: ((String, Throwable?) -> Unit)? = null
) {
    private fun headerInt(response: RemoteTranscriptionHttpResponse, name: String): Int? {
        return response.headers[name]?.toIntOrNull()
    }

    suspend fun transcribe(
        preparedAudio: PreparedAudio?,
        firebaseIdToken: String?
    ): RemoteTranscriptionOutcome {
        if (endpointUrl.isBlank()) {
            debugLog?.invoke("Remote transcription skipped: endpoint URL is blank", null)
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.ENDPOINT_NOT_CONFIGURED,
                    message = "Remote transcription endpoint is not configured."
                )
            )
        }

        if (preparedAudio == null) {
            debugLog?.invoke("Remote transcription skipped: prepared audio is missing", null)
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.PREPARED_AUDIO_MISSING,
                    message = "Prepare audio before requesting a remote transcript."
                )
            )
        }

        if (preparedAudio.wavBytes == null) {
            debugLog?.invoke("Remote transcription skipped: WAV bytes are missing", null)
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.WAV_BYTES_MISSING,
                    message = "Prepared audio is missing WAV bytes for upload."
                )
            )
        }

        if (firebaseIdToken.isNullOrBlank()) {
            debugLog?.invoke("Remote transcription skipped: Firebase ID token is missing", null)
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.AUTH_TOKEN_MISSING,
                    message = "Authentication token is missing. Please reopen the app and try again."
                )
            )
        }

        debugLog?.invoke(
            "Remote transcription request begin durationSec=${preparedAudio.durationSeconds} sampleRate=${preparedAudio.sampleRate} wavBytes=${preparedAudio.wavBytes.size}",
            null
        )

        return try {
            val response = httpClient.postWav(
                url = endpointUrl,
                bearerToken = firebaseIdToken,
                preparedAudio = preparedAudio
            )

            val responsePreview = response.body
                .replace("\n", " ")
                .replace("\r", " ")
                .take(180)
            debugLog?.invoke(
                "Remote transcription response status=${response.statusCode} bodyPreview=$responsePreview",
                null
            )

            when (response.statusCode) {
                200 -> {
                    val transcriptText = response.body.trim()
                    if (transcriptText.isBlank()) {
                        debugLog?.invoke("Remote transcription failed: empty transcript body", null)
                        RemoteTranscriptionOutcome.Failure(
                            RemoteTranscriptionFailure(
                                code = RemoteTranscriptionFailureCode.INVALID_RESPONSE,
                                message = "Remote transcription returned an empty transcript."
                            )
                        )
                    } else {
                        val durationMillis = response.headers["X-ClipScribe-Transcription-Duration-Ms"]?.toLongOrNull()
                        val freeTierDailyLimit = headerInt(response, "X-ClipScribe-Free-Limit")
                        val freeTierDailyUsed = headerInt(response, "X-ClipScribe-Free-Used")
                        val freeTierDailyRemaining = headerInt(response, "X-ClipScribe-Free-Remaining")
                        debugLog?.invoke(
                            "Remote transcription success chars=${transcriptText.length} durationMs=${durationMillis ?: -1} freeRemaining=${freeTierDailyRemaining ?: -1}",
                            null
                        )
                        RemoteTranscriptionOutcome.Success(
                            RemoteTranscriptionSuccess(
                                text = transcriptText,
                                durationMillis = durationMillis,
                                freeTierDailyLimit = freeTierDailyLimit,
                                freeTierDailyUsed = freeTierDailyUsed,
                                freeTierDailyRemaining = freeTierDailyRemaining
                            )
                        )
                    }
                }

                401, 403 -> RemoteTranscriptionOutcome.Failure(
                    RemoteTranscriptionFailure(
                        code = RemoteTranscriptionFailureCode.UNAUTHORIZED,
                        message = response.body.ifBlank {
                            "Authentication failed for remote transcription."
                        }
                    )
                ).also {
                    debugLog?.invoke("Remote transcription unauthorized", null)
                }

                429 -> RemoteTranscriptionOutcome.Failure(
                    RemoteTranscriptionFailure(
                        code = RemoteTranscriptionFailureCode.QUOTA_EXCEEDED,
                        message = response.body.ifBlank {
                            "Free transcript limit reached for today."
                        }
                    )
                ).also {
                    debugLog?.invoke("Remote transcription quota exceeded", null)
                }

                else -> RemoteTranscriptionOutcome.Failure(
                    RemoteTranscriptionFailure(
                        code = RemoteTranscriptionFailureCode.UPSTREAM_ERROR,
                        message = response.body.ifBlank {
                            "Remote transcription failed with status ${response.statusCode}."
                        }
                    )
                ).also {
                    debugLog?.invoke("Remote transcription upstream error status=${response.statusCode}", null)
                }
            }
        } catch (error: Exception) {
            debugLog?.invoke("Remote transcription network exception", error)
            RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.NETWORK_ERROR,
                    message = error.localizedMessage ?: "Network error while contacting remote transcription."
                )
            )
        }
    }
}
