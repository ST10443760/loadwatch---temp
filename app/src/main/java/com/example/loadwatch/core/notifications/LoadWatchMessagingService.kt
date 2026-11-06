package com.example.loadwatch.core.notifications

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.alert.StageAlertHandler
import com.example.loadwatch.data.alert.parseStageAlert
import com.example.loadwatch.data.repository.PushRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// Receives FCM messages and token refreshes. Stage alerts arrive as data messages, so this runs whether the app is in
// the foreground, in the background or closed, and decides itself whether to show anything.
// Reference: Firebase, "Receive messages in an Android app", firebase.google.com/docs/cloud-messaging/android/receive
@AndroidEntryPoint
class LoadWatchMessagingService : FirebaseMessagingService() {

    @Inject lateinit var stageAlertHandler: StageAlertHandler

    @Inject lateinit var pushRepository: PushRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Firebase rotated the token (reinstall, data cleared, or its own schedule); the API needs the new one
    override fun onNewToken(token: String) {
        Log.i(LogTags.PUSH, "FCM token refreshed")
        scope.launch { pushRepository.registerDevice(token) }
    }

    // Already on a background thread, with about 10 seconds before Android may stop the service
    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(LogTags.PUSH, "FCM message received from ${message.from}")
        val alert = parseStageAlert(message.data)
        if (alert == null) {
            Log.w(LogTags.PUSH, "Ignored an FCM message that is not a valid stage alert: ${message.data.keys}")
            return
        }
        runBlocking {
            withTimeoutOrNull(HANDLE_TIMEOUT_MILLIS) { stageAlertHandler.handle(alert) }
                ?: Log.w(LogTags.PUSH, "Handling the stage alert took too long and was stopped")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val HANDLE_TIMEOUT_MILLIS = 8_000L
    }
}
