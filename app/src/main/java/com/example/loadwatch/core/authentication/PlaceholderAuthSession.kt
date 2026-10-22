package com.example.loadwatch.core.authentication

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import javax.inject.Inject
import javax.inject.Singleton

// Always signed out, so launch routes through S02 Login. Replaced by the Firebase-backed session.
@Singleton
class PlaceholderAuthSession @Inject constructor() : AuthSession {

    override suspend fun currentUserId(): String? {
        Log.d(LogTags.AUTH, "Placeholder session: no signed-in user")
        return null
    }
}
