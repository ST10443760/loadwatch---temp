package com.example.loadwatch.core.network

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

// Attaches the Firebase ID token as "Authorization: Bearer <token>" to user-scoped routes only: settings,
// favourites and the outage report POST. Public routes are sent without it. The token itself is never logged.
class AuthInterceptor @Inject constructor(
    private val authSession: AuthSession,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.isUserScoped()) {
            return chain.proceed(request)
        }

        // Firebase hands back its cached token and refreshes it when it is close to expiry
        val token = try {
            runBlocking { authSession.idToken(forceRefresh = false) }
        } catch (e: Exception) {
            // Anything other than an IOException thrown here would crash OkHttp's worker thread
            throw IOException("Could not get a Firebase ID token", e)
        }

        if (token == null) {
            Log.w(LogTags.API, "${request.method} ${request.url.encodedPath} sent without a token: not signed in")
            return chain.proceed(request)
        }
        return chain.proceed(request.withBearer(token))
    }
}

internal const val AUTHORIZATION_HEADER = "Authorization"

internal fun Request.withBearer(token: String): Request =
    newBuilder().header(AUTHORIZATION_HEADER, "Bearer $token").build()

// The routes the endpoint specification marks as bearer and the Part 2 app calls
internal fun Request.isUserScoped(): Boolean {
    val path = url.encodedPath
    return path.contains("/api/users/") || (method == "POST" && path.endsWith("/api/outage-reports"))
}
