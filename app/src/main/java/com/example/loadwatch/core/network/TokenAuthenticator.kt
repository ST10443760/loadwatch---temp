package com.example.loadwatch.core.network

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import com.example.loadwatch.core.common.LogTags
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

// Handles 401 responses on user-scoped routes. The first 401 is retried once with a force-refreshed token,
// which covers a token that expired in flight. If that is rejected too, or nobody is signed in, the session is
// over: SessionExpiryNotifier tells the app to sign out cleanly. A network failure while refreshing is not
// treated as a sign-out; the 401 is simply returned to the caller.
class TokenAuthenticator @Inject constructor(
    private val authSession: AuthSession,
    private val sessionExpiryNotifier: SessionExpiryNotifier,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (!request.isUserScoped()) {
            return null
        }

        if (response.priorResponse != null) {
            sessionExpiryNotifier.notifySessionExpired()
            return null
        }

        val freshToken = try {
            runBlocking { authSession.idToken(forceRefresh = true) }
        } catch (e: Exception) {
            Log.w(LogTags.API, "401 on ${request.url.encodedPath}; could not refresh the token: ${e.javaClass.simpleName}")
            return null
        }

        if (freshToken == null) {
            sessionExpiryNotifier.notifySessionExpired()
            return null
        }

        Log.i(LogTags.API, "401 on ${request.method} ${request.url.encodedPath}; retrying once with a refreshed token")
        return request.withBearer(freshToken)
    }
}
