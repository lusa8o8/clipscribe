package com.example.transcription

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTranscriptionServiceIntegrationTest {
    @Test
    fun `returns success when endpoint responds with transcript text`() = runTest {
        val fakeClient = FakeRemoteTranscriptionHttpClient(
            response = RemoteTranscriptionHttpResponse(
                statusCode = 200,
                body = "useful transcript",
                headers = mapOf("X-ClipScribe-Transcription-Duration-Ms" to "842")
            )
        )
        val service = RemoteTranscriptionService(
            endpointUrl = "https://example.com/transcribe",
            httpClient = fakeClient
        )

        val result = service.transcribe(
            preparedAudio = samplePreparedAudio(),
            firebaseIdToken = "firebase-token"
        )

        assertTrue(result is RemoteTranscriptionOutcome.Success)
        val success = result as RemoteTranscriptionOutcome.Success
        assertEquals("useful transcript", success.value.text)
        assertEquals(842L, success.value.durationMillis)
        assertEquals("https://example.com/transcribe", fakeClient.lastUrl)
        assertEquals("firebase-token", fakeClient.lastBearerToken)
        assertEquals(16000, fakeClient.lastPreparedAudio?.sampleRate)
    }

    @Test
    fun `returns auth failure when firebase token is missing`() = runTest {
        val service = RemoteTranscriptionService(
            endpointUrl = "https://example.com/transcribe",
            httpClient = FakeRemoteTranscriptionHttpClient(
                response = RemoteTranscriptionHttpResponse(200, "ignored", emptyMap())
            )
        )

        val result = service.transcribe(
            preparedAudio = samplePreparedAudio(),
            firebaseIdToken = null
        )

        assertTrue(result is RemoteTranscriptionOutcome.Failure)
        val failure = result as RemoteTranscriptionOutcome.Failure
        assertEquals(RemoteTranscriptionFailureCode.AUTH_TOKEN_MISSING, failure.value.code)
    }

    @Test
    fun `returns upstream unauthorized when endpoint rejects bearer token`() = runTest {
        val service = RemoteTranscriptionService(
            endpointUrl = "https://example.com/transcribe",
            httpClient = FakeRemoteTranscriptionHttpClient(
                response = RemoteTranscriptionHttpResponse(
                    statusCode = 401,
                    body = "token invalid",
                    headers = emptyMap()
                )
            )
        )

        val result = service.transcribe(
            preparedAudio = samplePreparedAudio(),
            firebaseIdToken = "bad-token"
        )

        assertTrue(result is RemoteTranscriptionOutcome.Failure)
        val failure = result as RemoteTranscriptionOutcome.Failure
        assertEquals(RemoteTranscriptionFailureCode.UNAUTHORIZED, failure.value.code)
        assertEquals("token invalid", failure.value.message)
    }

    @Test
    fun `returns failure when prepared audio is missing wav bytes`() = runTest {
        val service = RemoteTranscriptionService(
            endpointUrl = "https://example.com/transcribe",
            httpClient = FakeRemoteTranscriptionHttpClient(
                response = RemoteTranscriptionHttpResponse(200, "ignored", emptyMap())
            )
        )

        val result = service.transcribe(
            preparedAudio = samplePreparedAudio(wavBytes = null),
            firebaseIdToken = "firebase-token"
        )

        assertTrue(result is RemoteTranscriptionOutcome.Failure)
        val failure = result as RemoteTranscriptionOutcome.Failure
        assertEquals(RemoteTranscriptionFailureCode.WAV_BYTES_MISSING, failure.value.code)
    }

    private fun samplePreparedAudio(wavBytes: ByteArray? = byteArrayOf(1, 2, 3, 4)): PreparedAudio {
        return PreparedAudio(
            floatSamples = floatArrayOf(0.1f, -0.2f, 0.3f),
            sampleRate = 16000,
            durationSeconds = 0.75,
            sampleCount = 3,
            wavBytes = wavBytes
        )
    }
}

private class FakeRemoteTranscriptionHttpClient(
    private val response: RemoteTranscriptionHttpResponse
) : RemoteTranscriptionHttpClient {
    var lastUrl: String? = null
    var lastBearerToken: String? = null
    var lastPreparedAudio: PreparedAudio? = null

    override suspend fun postWav(
        url: String,
        bearerToken: String,
        preparedAudio: PreparedAudio
    ): RemoteTranscriptionHttpResponse {
        lastUrl = url
        lastBearerToken = bearerToken
        lastPreparedAudio = preparedAudio
        return response
    }
}
