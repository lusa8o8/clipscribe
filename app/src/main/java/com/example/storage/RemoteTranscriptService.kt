package com.example.storage

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class RemoteTranscriptHttpResponse(
    val statusCode: Int,
    val body: String
)

interface RemoteTranscriptHttpClient {
    suspend fun request(
        url: String,
        method: String,
        bearerToken: String,
        jsonBody: String? = null
    ): RemoteTranscriptHttpResponse
}

class DefaultRemoteTranscriptHttpClient : RemoteTranscriptHttpClient {
    override suspend fun request(
        url: String,
        method: String,
        bearerToken: String,
        jsonBody: String?
    ): RemoteTranscriptHttpResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (jsonBody != null) {
                connection.outputStream.use { output ->
                    output.write(jsonBody.toByteArray(Charsets.UTF_8))
                }
            }

            val stream = if (connection.responseCode >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            } else {
                ""
            }

            RemoteTranscriptHttpResponse(connection.responseCode, body)
        } finally {
            connection.disconnect()
        }
    }
}

sealed interface RemoteTranscriptOutcome {
    data class Success(val transcripts: List<TranscriptEntity> = emptyList()) : RemoteTranscriptOutcome
    data class Failure(val message: String) : RemoteTranscriptOutcome
}

class RemoteTranscriptService(
    private val endpointUrl: String,
    private val httpClient: RemoteTranscriptHttpClient,
    private val debugLog: ((String, Throwable?) -> Unit)? = null
) {
    suspend fun saveTranscript(
        text: String,
        durationSeconds: Int,
        firebaseIdToken: String?
    ): RemoteTranscriptOutcome {
        if (endpointUrl.isBlank()) {
            return RemoteTranscriptOutcome.Failure("Transcript persistence endpoint is not configured.")
        }
        if (firebaseIdToken.isNullOrBlank()) {
            return RemoteTranscriptOutcome.Failure("Authentication token is missing.")
        }

        return try {
            val body = JSONObject()
                .put("text", text)
                .put("sourceDurationSeconds", durationSeconds)
                .toString()
            val response = httpClient.request(endpointUrl, "POST", firebaseIdToken, body)
            if (response.statusCode == 201) {
                RemoteTranscriptOutcome.Success(listOf(parseSavedTranscript(response.body)))
            } else {
                RemoteTranscriptOutcome.Failure(response.body.ifBlank { "Could not save transcript." })
            }
        } catch (error: Exception) {
            debugLog?.invoke("Remote transcript save failed", error)
            RemoteTranscriptOutcome.Failure(error.localizedMessage ?: "Could not save transcript.")
        }
    }

    suspend fun listTranscripts(firebaseIdToken: String?): RemoteTranscriptOutcome {
        if (endpointUrl.isBlank()) {
            return RemoteTranscriptOutcome.Failure("Transcript persistence endpoint is not configured.")
        }
        if (firebaseIdToken.isNullOrBlank()) {
            return RemoteTranscriptOutcome.Failure("Authentication token is missing.")
        }

        return try {
            val response = httpClient.request(endpointUrl, "GET", firebaseIdToken)
            if (response.statusCode == 200) {
                RemoteTranscriptOutcome.Success(parseTranscriptList(response.body))
            } else {
                RemoteTranscriptOutcome.Failure(response.body.ifBlank { "Could not load transcripts." })
            }
        } catch (error: Exception) {
            debugLog?.invoke("Remote transcript list failed", error)
            RemoteTranscriptOutcome.Failure(error.localizedMessage ?: "Could not load transcripts.")
        }
    }

    suspend fun deleteTranscript(id: String, firebaseIdToken: String?): RemoteTranscriptOutcome {
        if (endpointUrl.isBlank()) {
            return RemoteTranscriptOutcome.Failure("Transcript persistence endpoint is not configured.")
        }
        if (firebaseIdToken.isNullOrBlank()) {
            return RemoteTranscriptOutcome.Failure("Authentication token is missing.")
        }

        return try {
            val response = httpClient.request(
                url = "${endpointUrl.trimEnd('/')}/${java.net.URLEncoder.encode(id, "UTF-8")}",
                method = "DELETE",
                bearerToken = firebaseIdToken
            )
            if (response.statusCode == 204) {
                RemoteTranscriptOutcome.Success()
            } else {
                RemoteTranscriptOutcome.Failure(response.body.ifBlank { "Could not delete transcript." })
            }
        } catch (error: Exception) {
            debugLog?.invoke("Remote transcript delete failed", error)
            RemoteTranscriptOutcome.Failure(error.localizedMessage ?: "Could not delete transcript.")
        }
    }

    private fun parseSavedTranscript(body: String): TranscriptEntity {
        val transcript = JSONObject(body).getJSONObject("transcript")
        return transcriptFromJson(transcript)
    }

    private fun parseTranscriptList(body: String): List<TranscriptEntity> {
        val transcripts = JSONObject(body).optJSONArray("transcripts") ?: JSONArray()
        return List(transcripts.length()) { index ->
            transcriptFromJson(transcripts.getJSONObject(index))
        }
    }

    private fun transcriptFromJson(json: JSONObject): TranscriptEntity {
        val createdAt = json.optString("createdAt")
        val timestamp = runCatching {
            java.time.Instant.parse(createdAt).toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        return TranscriptEntity(
            id = json.getString("id"),
            text = json.getString("text"),
            timestamp = timestamp,
            durationSeconds = json.optInt("sourceDurationSeconds", 1).coerceAtLeast(1)
        )
    }
}
