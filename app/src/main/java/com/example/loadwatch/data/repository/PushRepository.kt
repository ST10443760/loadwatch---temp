package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.notifications.PushMessaging
import com.example.loadwatch.core.notifications.PushPreferences
import com.example.loadwatch.core.notifications.areaTopic
import com.example.loadwatch.data.remote.PushRemoteDataSource
import com.example.loadwatch.data.remote.RemoteResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

// Links this phone to the signed-in account for push notifications, and keeps it subscribed to the FCM topic of
// the user's selected area only, so stage-change alerts arrive for that area and no other.
@Singleton
class PushRepository @Inject constructor(
    private val authSession: AuthSession,
    private val remote: PushRemoteDataSource,
    private val messaging: PushMessaging,
    private val preferences: PushPreferences,
) {

    // Sends the FCM token to the API. onNewToken passes the new token; otherwise the current one is fetched.
    // Returns whether the API stored it. A failure is retried on the next launch or token refresh.
    suspend fun registerDevice(token: String? = null): Boolean {
        if (authSession.currentUserId() == null) {
            Log.d(LogTags.PUSH, "Not signed in; the FCM token is sent after sign-in")
            return false
        }
        val fcmToken = token ?: try {
            messaging.token()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LogTags.PUSH, "Could not get the FCM token: ${e.javaClass.simpleName}")
            return false
        }

        return when (remote.registerDevice(fcmToken)) {
            is RemoteResult.Success -> true.also { Log.i(LogTags.PUSH, "FCM token registered with the API") }
            else -> false.also { Log.w(LogTags.PUSH, "FCM token not registered; it is sent again on the next launch") }
        }
    }

    // Moves the subscription to the area's topic, or off every topic when areaId is null (signed out, or no area
    // chosen yet). The old topic is left before the new one is joined, so alerts never arrive for two areas.
    suspend fun followArea(areaId: String?) {
        val wanted = areaId?.let(::areaTopic)
        val current = preferences.subscribedTopic
        if (wanted == current) return

        try {
            if (current != null) {
                messaging.unsubscribe(current)
                preferences.subscribedTopic = null
                Log.i(LogTags.PUSH, "Unsubscribed from $current")
            }
            if (wanted != null) {
                messaging.subscribe(wanted)
                preferences.subscribedTopic = wanted
                Log.i(LogTags.PUSH, "Subscribed to $wanted")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing is recorded for the step that failed, so the next area change or launch tries it again
            Log.w(LogTags.PUSH, "Could not move the topic subscription to $wanted: ${e.javaClass.simpleName}")
        }
    }
}
