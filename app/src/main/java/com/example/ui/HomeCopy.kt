package com.example.ui

import com.example.auth.AuthStatus

object HomeCopy {
    fun cloudStatusBadge(status: AuthStatus): String {
        return when (status) {
            AuthStatus.SIGNED_IN_ANONYMOUSLY -> "Cloud transcription - Beta account"
            AuthStatus.SIGNED_IN_ACCOUNT -> "Cloud transcription - Signed in"
            AuthStatus.SIGNING_IN,
            AuthStatus.UPGRADING_ACCOUNT,
            AuthStatus.NOT_STARTED -> "Cloud transcription - Signing in"
            AuthStatus.ERROR -> "Cloud transcription - Auth issue"
        }
    }
}
