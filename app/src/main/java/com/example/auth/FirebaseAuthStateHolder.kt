package com.example.auth

import android.content.Context
import com.example.core.DebugFileLog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseAuthStateHolder {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun getLatestIdToken(): String? = _state.value.idToken

    fun startAnonymousSignIn(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val existingUser = auth.currentUser
        if (existingUser != null) {
            DebugFileLog.write(context, "Firebase auth existing anonymous user uid=${existingUser.uid.takeLast(6)}")
            existingUser.getIdToken(false)
                .addOnSuccessListener { tokenResult ->
                    _state.value = AuthState(
                        status = AuthStatus.SIGNED_IN_ANONYMOUSLY,
                        uid = existingUser.uid,
                        idToken = tokenResult.token
                    )
                    DebugFileLog.write(
                        context,
                        "Firebase existing user token ready uid=${existingUser.uid.takeLast(6)} token=${if (tokenResult.token.isNullOrBlank()) "missing" else "ready"}"
                    )
                }
                .addOnFailureListener { error ->
                    _state.value = AuthState(
                        status = AuthStatus.SIGNED_IN_ANONYMOUSLY,
                        uid = existingUser.uid,
                        idToken = null,
                        errorMessage = error.message
                    )
                    DebugFileLog.write(context, "Firebase existing user token fetch failed", error)
                }
            return
        }

        _state.value = AuthState(status = AuthStatus.SIGNING_IN)
        DebugFileLog.write(context, "Firebase anonymous sign-in begin")
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                val uid = user?.uid
                DebugFileLog.write(context, "Firebase anonymous sign-in success uid=${uid?.takeLast(6) ?: "missing"}")
                user?.getIdToken(false)
                    ?.addOnSuccessListener { tokenResult ->
                        _state.value = AuthState(
                            status = AuthStatus.SIGNED_IN_ANONYMOUSLY,
                            uid = uid,
                            idToken = tokenResult.token
                        )
                        DebugFileLog.write(
                            context,
                            "Firebase anonymous token ready uid=${uid?.takeLast(6) ?: "missing"} token=${if (tokenResult.token.isNullOrBlank()) "missing" else "ready"}"
                        )
                    }
                    ?.addOnFailureListener { error ->
                        _state.value = AuthState(
                            status = AuthStatus.SIGNED_IN_ANONYMOUSLY,
                            uid = uid,
                            idToken = null,
                            errorMessage = error.message
                        )
                        DebugFileLog.write(context, "Firebase anonymous token fetch failed", error)
                    }
            }
            .addOnFailureListener { error ->
                _state.value = AuthState(
                    status = AuthStatus.ERROR,
                    errorMessage = error.message
                )
                DebugFileLog.write(context, "Firebase anonymous sign-in failed", error)
            }
    }
}
