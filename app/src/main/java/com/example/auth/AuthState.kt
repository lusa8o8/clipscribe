package com.example.auth

data class AuthState(
    val status: AuthStatus = AuthStatus.NOT_STARTED,
    val uid: String? = null,
    val email: String? = null,
    val isAnonymous: Boolean = true,
    val idToken: String? = null,
    val errorMessage: String? = null
) {
    val canPersistTranscripts: Boolean
        get() = status == AuthStatus.SIGNED_IN_EMAIL
}

enum class AuthStatus {
    NOT_STARTED,
    SIGNING_IN,
    UPGRADING_ACCOUNT,
    SIGNED_IN_ANONYMOUSLY,
    SIGNED_IN_EMAIL,
    ERROR
}
