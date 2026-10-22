package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.local.UserDataLocalDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInResult {
    data object Success : SignInResult
    data object Failed : SignInResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val authSession: AuthSession,
    private val userData: UserDataLocalDataSource,
    sessionExpiryNotifier: SessionExpiryNotifier,
) {
    val signedInUserId: StateFlow<String?> = authSession.signedInUserId

    // Emits when the API rejected the session; the app shell responds by signing out
    val sessionExpired: SharedFlow<Unit> = sessionExpiryNotifier.sessionExpired

    suspend fun signInWithGoogleIdToken(googleIdToken: String): SignInResult =
        try {
            authSession.signInWithGoogleIdToken(googleIdToken)
            SignInResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LogTags.AUTH, "Firebase sign-in failed: ${e.javaClass.simpleName}")
            SignInResult.Failed
        }

    // Signs out of Firebase, then clears everything user-scoped from Room while keeping the public area
    // catalogue (FR-AUTH-04). Runs to completion even if the caller's scope is cancelled mid-way.
    suspend fun signOut() = withContext(NonCancellable) {
        authSession.signOut()
        userData.clearAll()
    }
}
