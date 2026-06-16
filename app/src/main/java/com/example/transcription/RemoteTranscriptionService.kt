package com.example.transcription

data class RemoteTranscriptionSuccess(
    val text: String,
    val durationMillis: Long?
)

enum class RemoteTranscriptionFailureCode {
    ENDPOINT_NOT_CONFIGURED,
    AUTH_TOKEN_MISSING,
    PREPARED_AUDIO_MISSING,
    WAV_BYTES_MISSING,
    UNAUTHORIZED,
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
    private val httpClient: RemoteTranscriptionHttpClient
) {
    suspend fun transcribe(
        preparedAudio: PreparedAudio?,
        firebaseIdToken: String?
    ): RemoteTranscriptionOutcome {
        if (endpointUrl.isBlank()) {
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.ENDPOINT_NOT_CONFIGURED,
                    message = "Remote transcription endpoint is not configured."
                )
            )
        }

        if (preparedAudio == null) {
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.PREPARED_AUDIO_MISSING,
                    message = "Prepare audio before requesting a remote transcript."
                )
            )
        }

        if (preparedAudio.wavBytes == null) {
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.WAV_BYTES_MISSING,
                    message = "Prepared audio is missing WAV bytes for upload."
                )
            )
        }

        if (firebaseIdToken.isNullOrBlank()) {
            return RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.AUTH_TOKEN_MISSING,
                    message = "Authentication token is missing. Please reopen the app and try again."
                )
            )
        }

        return try {
            val response = httpClient.postWav(
                url = endpointUrl,
                bearerToken = firebaseIdToken,
                preparedAudio = preparedAudio
            )

            when (response.statusCode) {
                200 -> {
                    val transcriptText = response.body.trim()
                    if (transcriptText.isBlank()) {
                        RemoteTranscriptionOutcome.Failure(
                            RemoteTranscriptionFailure(
                                code = RemoteTranscriptionFailureCode.INVALID_RESPONSE,
                                message = "Remote transcription returned an empty transcript."
                            )
                        )
                    } else {
                        val durationMillis = response.headers["X-ClipScribe-Transcription-Duration-Ms"]?.toLongOrNull()
                        RemoteTranscriptionOutcome.Success(
                            RemoteTranscriptionSuccess(
                                text = transcriptText,
                                durationMillis = durationMillis
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
                )

                else -> RemoteTranscriptionOutcome.Failure(
                    RemoteTranscriptionFailure(
                        code = RemoteTranscriptionFailureCode.UPSTREAM_ERROR,
                        message = response.body.ifBlank {
                            "Remote transcription failed with status ${response.statusCode}."
                        }
                    )
                )
            }
        } catch (error: Exception) {
            RemoteTranscriptionOutcome.Failure(
                RemoteTranscriptionFailure(
                    code = RemoteTranscriptionFailureCode.NETWORK_ERROR,
                    message = error.localizedMessage ?: "Network error while contacting remote transcription."
                )
            )
        }
    }
}
