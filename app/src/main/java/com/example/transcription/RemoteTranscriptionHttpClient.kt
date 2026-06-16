package com.example.transcription

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RemoteTranscriptionHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>
)

interface RemoteTranscriptionHttpClient {
    suspend fun postWav(
        url: String,
        bearerToken: String,
        preparedAudio: PreparedAudio
    ): RemoteTranscriptionHttpResponse
}

class DefaultRemoteTranscriptionHttpClient : RemoteTranscriptionHttpClient {
    override suspend fun postWav(
        url: String,
        bearerToken: String,
        preparedAudio: PreparedAudio
    ): RemoteTranscriptionHttpResponse = withContext(Dispatchers.IO) {
        val wavBytes = preparedAudio.wavBytes ?: error("Prepared audio is missing wavBytes.")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("Content-Type", "audio/wav")
            setRequestProperty("X-ClipScribe-Sample-Rate", preparedAudio.sampleRate.toString())
            setRequestProperty("X-ClipScribe-Duration-Sec", preparedAudio.durationSeconds.toString())
        }

        try {
            connection.outputStream.use { output ->
                output.write(wavBytes)
            }

            val stream = if (connection.responseCode >= 400) {
                connection.errorStream
            } else {
                connection.inputStream
            }

            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText()
                }
            } else {
                ""
            }

            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapValues { entry -> entry.value.firstOrNull().orEmpty() }

            RemoteTranscriptionHttpResponse(
                statusCode = connection.responseCode,
                body = body,
                headers = headers
            )
        } finally {
            connection.disconnect()
        }
    }
}
