package com.example.auth

data class AuthState(
    val status: AuthStatus = AuthStatus.NOT_STARTED,
    val uid: String? = null,
    val idToken: String? = null,
    val errorMessage: String? = null
)

enum class AuthStatus {
    NOT_STARTED,
    SIGNING_IN,
    SIGNED_IN_ANONYMOUSLY,
    ERROR
}
