package com.example.storage

import com.example.auth.AuthState
import com.example.auth.AuthStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptSaveControllerTest {

    @Test
    fun `anonymous auth cannot save transcript`() = runTest {
        val controller = TranscriptSaveController(
            repository = TranscriptRepository(LocalTranscriptStore()),
            authStateProvider = {
                AuthState(
                    status = AuthStatus.SIGNED_IN_ANONYMOUSLY,
                    uid = "anon-user",
                    isAnonymous = true
                )
            }
        )

        val result = controller.saveTranscript(
            text = "Useful note from a video",
            sourceDurationSeconds = 18.4
        )

        assertEquals(SaveTranscriptResult.AUTH_REQUIRED, result)
        assertTrue(controller.transcripts.value.isEmpty())
    }

    @Test
    fun `email account can save transcript`() = runTest {
        val controller = TranscriptSaveController(
            repository = TranscriptRepository(LocalTranscriptStore()),
            authStateProvider = {
                AuthState(
                    status = AuthStatus.SIGNED_IN_EMAIL,
                    uid = "real-user",
                    email = "person@example.com",
                    isAnonymous = false
                )
            }
        )

        val result = controller.saveTranscript(
            text = "Useful note from a podcast",
            sourceDurationSeconds = 21.7
        )

        assertEquals(SaveTranscriptResult.SUCCESS, result)
        assertEquals(1, controller.transcripts.value.size)
        assertEquals("Useful note from a podcast", controller.transcripts.value.first().text)
        assertEquals(22, controller.transcripts.value.first().durationSeconds)
    }
}
