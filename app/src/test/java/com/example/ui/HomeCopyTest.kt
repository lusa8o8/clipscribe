package com.example.ui

import com.example.auth.AuthStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCopyTest {
    @Test
    fun `badge says beta account for anonymous users`() {
        assertEquals(
            "Cloud transcription - Beta account",
            HomeCopy.cloudStatusBadge(AuthStatus.SIGNED_IN_ANONYMOUSLY)
        )
    }

    @Test
    fun `badge says signed in for Google users`() {
        assertEquals(
            "Cloud transcription - Signed in",
            HomeCopy.cloudStatusBadge(AuthStatus.SIGNED_IN_ACCOUNT)
        )
    }

    @Test
    fun `badge says signing in for pending auth states`() {
        assertEquals(
            "Cloud transcription - Signing in",
            HomeCopy.cloudStatusBadge(AuthStatus.NOT_STARTED)
        )
        assertEquals(
            "Cloud transcription - Signing in",
            HomeCopy.cloudStatusBadge(AuthStatus.SIGNING_IN)
        )
        assertEquals(
            "Cloud transcription - Signing in",
            HomeCopy.cloudStatusBadge(AuthStatus.UPGRADING_ACCOUNT)
        )
    }

    @Test
    fun `badge says auth issue for auth errors`() {
        assertEquals(
            "Cloud transcription - Auth issue",
            HomeCopy.cloudStatusBadge(AuthStatus.ERROR)
        )
    }
}
