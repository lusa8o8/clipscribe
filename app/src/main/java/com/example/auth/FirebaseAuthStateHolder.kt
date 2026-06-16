package com.example.auth

import android.content.Context
import com.example.core.DebugFileLog
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseAuthStateHolder {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun getLatestIdToken(): String? = _state.value.idToken

    fun getCurrentState(): AuthState = _state.value

    private fun signedInStatusFor(user: FirebaseUser): AuthStatus {
        return if (user.isAnonymous) {
            AuthStatus.SIGNED_IN_ANONYMOUSLY
        } else {
            AuthStatus.SIGNED_IN_EMAIL
        }
    }

    private fun publishSignedInState(
        context: Context,
        user: FirebaseUser,
        idToken: String?,
        errorMessage: String? = null
    ) {
        _state.value = AuthState(
            status = signedInStatusFor(user),
            uid = user.uid,
            email = user.email,
            isAnonymous = user.isAnonymous,
            idToken = idToken,
            errorMessage = errorMessage
        )
        DebugFileLog.write(
            context,
            "Firebase auth state ready anonymous=${user.isAnonymous} uid=${user.uid.takeLast(6)} email=${user.email ?: "none"} token=${if (idToken.isNullOrBlank()) "missing" else "ready"}"
        )
    }

    private fun fetchTokenAndPublish(context: Context, user: FirebaseUser) {
        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                publishSignedInState(context, user, tokenResult.token)
            }
            .addOnFailureListener { error ->
                publishSignedInState(
                    context = context,
                    user = user,
                    idToken = null,
                    errorMessage = error.message
                )
                DebugFileLog.write(context, "Firebase token fetch failed", error)
            }
    }

    fun startAnonymousSignIn(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val existingUser = auth.currentUser
        if (existingUser != null) {
            DebugFileLog.write(
                context,
                "Firebase auth existing user anonymous=${existingUser.isAnonymous} uid=${existingUser.uid.takeLast(6)} email=${existingUser.email ?: "none"}"
            )
            fetchTokenAndPublish(context, existingUser)
            return
        }

        _state.value = AuthState(status = AuthStatus.SIGNING_IN)
        DebugFileLog.write(context, "Firebase anonymous sign-in begin")
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                val uid = user?.uid
                DebugFileLog.write(context, "Firebase anonymous sign-in success uid=${uid?.takeLast(6) ?: "missing"}")
                if (user != null) {
                    fetchTokenAndPublish(context, user)
                }
            }
            .addOnFailureListener { error ->
                _state.value = AuthState(
                    status = AuthStatus.ERROR,
                    isAnonymous = true,
                    errorMessage = error.message
                )
                DebugFileLog.write(context, "Firebase anonymous sign-in failed", error)
            }
    }

    fun upgradeAnonymousAccount(
        context: Context,
        email: String,
        password: String,
        onComplete: (AccountUpgradeResult, String?) -> Unit
    ) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || password.length < 6) {
            onComplete(AccountUpgradeResult.INVALID_INPUT, "Enter a valid email and a password with at least 6 characters.")
            return
        }

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        _state.value = _state.value.copy(
            status = AuthStatus.UPGRADING_ACCOUNT,
            errorMessage = null
        )

        val credential = EmailAuthProvider.getCredential(normalizedEmail, password)

        fun completeWithSignedInUser(user: FirebaseUser?) {
            if (user == null) {
                _state.value = _state.value.copy(status = AuthStatus.ERROR, errorMessage = "No Firebase user returned.")
                onComplete(AccountUpgradeResult.ERROR, "No Firebase user returned.")
                return
            }

            user.getIdToken(true)
                .addOnSuccessListener { tokenResult ->
                    publishSignedInState(context, user, tokenResult.token)
                    onComplete(AccountUpgradeResult.SUCCESS, null)
                }
                .addOnFailureListener { error ->
                    publishSignedInState(context, user, null, error.message)
                    onComplete(AccountUpgradeResult.ERROR, error.message ?: "Could not refresh account token.")
                }
        }

        fun signInExistingUser() {
            auth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener { result ->
                    DebugFileLog.write(context, "Firebase email sign-in success email=$normalizedEmail")
                    completeWithSignedInUser(result.user)
                }
                .addOnFailureListener { error ->
                    _state.value = _state.value.copy(status = AuthStatus.ERROR, errorMessage = error.message)
                    DebugFileLog.write(context, "Firebase email sign-in failed email=$normalizedEmail", error)
                    onComplete(AccountUpgradeResult.ERROR, error.message ?: "Could not sign in with that email.")
                }
        }

        if (currentUser != null && currentUser.isAnonymous) {
            currentUser.linkWithCredential(credential)
                .addOnSuccessListener { result ->
                    DebugFileLog.write(context, "Firebase anonymous account upgraded email=$normalizedEmail")
                    completeWithSignedInUser(result.user)
                }
                .addOnFailureListener { error ->
                    if (error is FirebaseAuthUserCollisionException) {
                        DebugFileLog.write(context, "Firebase upgrade collision, signing into existing email=$normalizedEmail", error)
                        signInExistingUser()
                    } else {
                        _state.value = _state.value.copy(status = AuthStatus.ERROR, errorMessage = error.message)
                        DebugFileLog.write(context, "Firebase account upgrade failed email=$normalizedEmail", error)
                        onComplete(AccountUpgradeResult.ERROR, error.message ?: "Could not upgrade account.")
                    }
                }
            return
        }

        signInExistingUser()
    }
}
