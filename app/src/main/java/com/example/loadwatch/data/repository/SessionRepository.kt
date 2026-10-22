package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.local.SettingsLocalDataSource
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class SessionStatus {
    // Route to S02 SSO Login
    SignedOut,

    // Signed in but no default area yet: route to S03 Area Onboarding
    NeedsArea,

    // Route to S04 Home
    Ready,
}

// Resolves where S01 Splash (and S02 after sign-in) should send the user. The Firebase session and Room
// settings both survive a force-stop, so a returning user goes straight to Home, even offline (FR-AUTH-02).
class SessionRepository @Inject constructor(
    private val authSession: AuthSession,
    private val settingsLocalDataSource: SettingsLocalDataSource,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun resolveSession(): SessionStatus {
        val userId = authSession.currentUserId()
        val status = when {
            userId == null -> SessionStatus.SignedOut
            settingsLocalDataSource.defaultAreaId(userId) != null -> SessionStatus.Ready
            // A new device or reinstall: the area chosen elsewhere is on the API (FR-AREA-03). Offline, or if the
            // API is slow, onboarding asks again rather than leaving the user on the splash screen.
            restoreDefaultAreaFromServer(userId) -> SessionStatus.Ready
            else -> SessionStatus.NeedsArea
        }
        Log.i(LogTags.AUTH, "Session resolved as $status")
        return status
    }

    private suspend fun restoreDefaultAreaFromServer(userId: String): Boolean {
        val restored = withTimeoutOrNull(RESTORE_TIMEOUT_MILLIS) { settingsRepository.restoreFromServer(userId) }
        return restored?.defaultAreaId != null
    }

    private companion object {
        const val RESTORE_TIMEOUT_MILLIS = 4_000L
    }
}
