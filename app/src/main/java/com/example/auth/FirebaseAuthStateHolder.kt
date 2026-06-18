package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.core.DebugFileLog
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FirebaseAuthStateHolder {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun getLatestIdToken(): String? = _state.value.idToken

    fun getCurrentState(): AuthState = _state.value

    private fun resolveDefaultWebClientId(context: Context): String? {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resourceId == 0) {
            return null
        }
        return context.getString(resourceId).takeIf { it.isNotBlank() }
    }

    private fun signedInStatusFor(user: FirebaseUser): AuthStatus {
        return if (user.isAnonymous) {
            AuthStatus.SIGNED_IN_ANONYMOUSLY
        } else {
            AuthStatus.SIGNED_IN_ACCOUNT
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

    suspend fun signInWithGoogleAccount(context: Context): Pair<AccountUpgradeResult, String?> {
        val auth = FirebaseAuth.getInstance()
        val previousState = _state.value
        _state.value = _state.value.copy(
            status = AuthStatus.UPGRADING_ACCOUNT,
            errorMessage = null
        )

        return try {
            val serverClientId = resolveDefaultWebClientId(context)
            if (serverClientId.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    status = AuthStatus.ERROR,
                    errorMessage = "Google sign-in is not configured."
                )
                return AccountUpgradeResult.ERROR to "Google sign-in is not configured."
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = CredentialManager.create(context).getCredential(
                context = context,
                request = request
            )

            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                _state.value = _state.value.copy(
                    status = AuthStatus.ERROR,
                    errorMessage = "Google sign-in did not return a Google ID token."
                )
                return AccountUpgradeResult.ERROR to "Google sign-in did not return a Google ID token."
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            val currentUser = auth.currentUser

            val signedInUser = if (currentUser != null && currentUser.isAnonymous) {
                try {
                    DebugFileLog.write(context, "Firebase Google account link begin")
                    currentUser.linkWithCredential(firebaseCredential).await().user
                } catch (error: FirebaseAuthUserCollisionException) {
                    DebugFileLog.write(context, "Firebase Google account collision, signing into existing Google account", error)
                    auth.signInWithCredential(firebaseCredential).await().user
                }
            } else {
                DebugFileLog.write(context, "Firebase Google sign-in begin")
                auth.signInWithCredential(firebaseCredential).await().user
            }

            if (signedInUser == null) {
                _state.value = _state.value.copy(
                    status = AuthStatus.ERROR,
                    errorMessage = "No Firebase user returned."
                )
                AccountUpgradeResult.ERROR to "No Firebase user returned."
            } else {
                val token = signedInUser.getIdToken(true).await().token
                publishSignedInState(context, signedInUser, token)
                DebugFileLog.write(
                    context,
                    "Firebase Google sign-in success uid=${signedInUser.uid.takeLast(6)} email=${signedInUser.email ?: "none"}"
                )
                AccountUpgradeResult.SUCCESS to null
            }
        } catch (error: GetCredentialException) {
            _state.value = previousState
            DebugFileLog.write(context, "Google credential picker failed or was cancelled", error)
            AccountUpgradeResult.ERROR to (error.message ?: "Google sign-in was cancelled or failed.")
        } catch (error: GoogleIdTokenParsingException) {
            _state.value = _state.value.copy(status = AuthStatus.ERROR, errorMessage = error.message)
            DebugFileLog.write(context, "Google ID token parsing failed", error)
            AccountUpgradeResult.ERROR to (error.message ?: "Could not read the Google sign-in token.")
        } catch (error: Exception) {
            _state.value = _state.value.copy(status = AuthStatus.ERROR, errorMessage = error.message)
            DebugFileLog.write(context, "Firebase Google sign-in failed", error)
            AccountUpgradeResult.ERROR to (error.message ?: "Could not sign in with Google.")
        }
    }
}
