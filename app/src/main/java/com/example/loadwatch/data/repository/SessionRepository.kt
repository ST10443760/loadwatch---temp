package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.local.SettingsLocalDataSource
import javax.inject.Inject

enum class SessionStatus {
    // Route to S02 SSO Login
    SignedOut,

    // Signed in but no default area yet: route to S03 Area Onboarding
    NeedsArea,

    // Route to S04 Home
    Ready,
}

// Resolves where S01 Splash should send the user, using only local state so it works offline.
class SessionRepository @Inject constructor(
    private val authSession: AuthSession,
    private val settingsLocalDataSource: SettingsLocalDataSource,
) {
    suspend fun resolveSession(): SessionStatus {
        val userId = authSession.currentUserId()
        val status = when {
            userId == null -> SessionStatus.SignedOut
            settingsLocalDataSource.defaultAreaId(userId) == null -> SessionStatus.NeedsArea
            else -> SessionStatus.Ready
        }
        Log.i(LogTags.AUTH, "Session resolved as $status")
        return status
    }
}
