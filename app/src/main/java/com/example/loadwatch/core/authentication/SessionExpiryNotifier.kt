package com.example.loadwatch.core.authentication

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// Raised when the API rejects the session even with a freshly refreshed token. The app shell listens for it,
// signs out cleanly (clearing user-scoped data) and returns to S02 Login.
@Singleton
class SessionExpiryNotifier @Inject constructor() {

    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val sessionExpired: SharedFlow<Unit> = events.asSharedFlow()

    fun notifySessionExpired() {
        Log.w(LogTags.AUTH, "The API rejected the session; signing out")
        events.tryEmit(Unit)
    }
}
