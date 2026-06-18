package com.example.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTranscriptServiceTest {
    @Test
    fun `save transcript posts json and maps saved transcript`() = runTest {
        val client = FakeRemoteTranscriptHttpClient(
            RemoteTranscriptHttpResponse(
                statusCode = 201,
                body = """
                    {
                      "transcript": {
                        "id": "tx-1",
                        "text": "Saved text",
                        "sourceDurationSeconds": 19,
                        "createdAt": "2026-06-16T12:30:00.000Z"
                      }
                    }
                """.trimIndent()
            )
        )
        val service = RemoteTranscriptService(
            endpointUrl = "https://example.com/transcripts",
            httpClient = client
        )

        val result = service.saveTranscript(
            text = "Saved text",
            durationSeconds = 19,
            firebaseIdToken = "token"
        )

        assertTrue(result is RemoteTranscriptOutcome.Success)
        val transcript = (result as RemoteTranscriptOutcome.Success).transcripts.single()
        assertEquals("tx-1", transcript.id)
        assertEquals("Saved text", transcript.text)
        assertEquals(19, transcript.durationSeconds)
        assertEquals("POST", client.lastMethod)
        assertEquals("token", client.lastBearerToken)
        assertTrue(client.lastJsonBody?.contains("\"sourceDurationSeconds\":19") == true)
    }

    @Test
    fun `list transcripts maps response order`() = runTest {
        val client = FakeRemoteTranscriptHttpClient(
            RemoteTranscriptHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "transcripts": [
                        {
                          "id": "new",
                          "text": "New text",
                          "sourceDurationSeconds": 20,
                          "createdAt": "2026-06-16T13:00:00.000Z"
                        },
                        {
                          "id": "old",
                          "text": "Old text",
                          "sourceDurationSeconds": 10,
                          "createdAt": "2026-06-16T10:00:00.000Z"
                        }
                      ]
                    }
                """.trimIndent()
            )
        )
        val service = RemoteTranscriptService(
            endpointUrl = "https://example.com/transcripts",
            httpClient = client
        )

        val result = service.listTranscripts(firebaseIdToken = "token")

        assertTrue(result is RemoteTranscriptOutcome.Success)
        val transcripts = (result as RemoteTranscriptOutcome.Success).transcripts
        assertEquals(listOf("new", "old"), transcripts.map { it.id })
    }

    @Test
    fun `missing firebase token returns failure`() = runTest {
        val service = RemoteTranscriptService(
            endpointUrl = "https://example.com/transcripts",
            httpClient = FakeRemoteTranscriptHttpClient(RemoteTranscriptHttpResponse(200, "{}"))
        )

        val result = service.saveTranscript(
            text = "Saved text",
            durationSeconds = 19,
            firebaseIdToken = null
        )

        assertTrue(result is RemoteTranscriptOutcome.Failure)
        assertEquals(
            "Authentication token is missing.",
            (result as RemoteTranscriptOutcome.Failure).message
        )
    }
}

private class FakeRemoteTranscriptHttpClient(
    private val response: RemoteTranscriptHttpResponse
) : RemoteTranscriptHttpClient {
    var lastUrl: String? = null
    var lastMethod: String? = null
    var lastBearerToken: String? = null
    var lastJsonBody: String? = null

    override suspend fun request(
        url: String,
        method: String,
        bearerToken: String,
        jsonBody: String?
    ): RemoteTranscriptHttpResponse {
        lastUrl = url
        lastMethod = method
        lastBearerToken = bearerToken
        lastJsonBody = jsonBody
        return response
    }
}
