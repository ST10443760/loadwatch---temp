package com.example.loadwatch.core.network

import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.authentication.SessionExpiryNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The Firebase token goes only to the routes the API marks [Authorize], and a 401 on them is retried once
class AuthInterceptorTest {

    private val auth = TestAuthSession()

    @Test
    fun `the token is sent with the sync batch`() {
        assertEquals("Bearer cached-token", authorizationSentFor("POST", "api/sync"))
    }

    @Test
    fun `the token is sent with the device registration`() {
        assertEquals("Bearer cached-token", authorizationSentFor("POST", "api/notifications/registrations"))
    }

    @Test
    fun `the token is sent with settings and favourites`() {
        assertEquals("Bearer cached-token", authorizationSentFor("GET", "api/users/user-1/settings"))
        assertEquals("Bearer cached-token", authorizationSentFor("PUT", "api/users/user-1/settings"))
        assertEquals("Bearer cached-token", authorizationSentFor("GET", "api/users/user-1/favourites"))
        assertEquals("Bearer cached-token", authorizationSentFor("POST", "api/users/user-1/favourites"))
        assertEquals("Bearer cached-token", authorizationSentFor("DELETE", "api/users/user-1/favourites/eth-berea"))
    }

    @Test
    fun `the token is sent with a new outage report`() {
        assertEquals("Bearer cached-token", authorizationSentFor("POST", "api/outage-reports"))
    }

    @Test
    fun `public routes are sent without a token`() {
        assertNull(authorizationSentFor("GET", "api/areas?q=berea"))
        assertNull(authorizationSentFor("GET", "api/areas/eth-berea"))
        assertNull(authorizationSentFor("GET", "api/schedules?areaId=eth-berea"))
        assertNull(authorizationSentFor("GET", "api/schedules/next?areaId=eth-berea"))
        assertNull(authorizationSentFor("GET", "api/stages/current"))
        assertNull(authorizationSentFor("GET", "api/areas/eth-berea/history?month=2026-11"))
        assertNull(authorizationSentFor("GET", "api/areas/eth-berea/outage-reports"))
        assertNull(authorizationSentFor("GET", "api/areas/eth-berea/accuracy"))
    }

    @Test
    fun `a user-scoped route goes without a token when nobody is signed in`() {
        auth.signedInUserId.value = null
        assertNull(authorizationSentFor("POST", "api/sync"))
    }

    @Test
    fun `a 401 on the sync batch is retried once with a refreshed token`() {
        val notifier = SessionExpiryNotifier()
        val retry = TokenAuthenticator(auth, notifier).authenticate(null, unauthorized(request("POST", "api/sync")))

        assertEquals("Bearer fresh-token", retry?.header(AUTHORIZATION_HEADER))
        assertEquals("/api/sync", retry?.url?.encodedPath)
        assertEquals(1, auth.forcedRefreshes)
    }

    @Test
    fun `a second 401 on the sync batch is not retried again`() {
        val first = unauthorized(request("POST", "api/sync"))
        val second = unauthorized(request("POST", "api/sync")).newBuilder().priorResponse(first).build()

        assertNull(TokenAuthenticator(auth, SessionExpiryNotifier()).authenticate(null, second))
        assertEquals(0, auth.forcedRefreshes)
    }

    @Test
    fun `a 401 on a public route is not retried`() {
        assertNull(TokenAuthenticator(auth, SessionExpiryNotifier()).authenticate(null, unauthorized(request("GET", "api/areas"))))
        assertEquals(0, auth.forcedRefreshes)
    }

    // Runs the request through AuthInterceptor and returns the Authorization header that would have been sent.
    // The last interceptor answers in place of the network.
    private fun authorizationSentFor(method: String, path: String): String? {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(auth))
            .addInterceptor(Interceptor { chain ->
                sent = chain.request()
                ok(chain.request())
            })
            .build()
        client.newCall(request(method, path)).execute().close()
        return sent!!.header(AUTHORIZATION_HEADER)
    }

    private fun request(method: String, path: String): Request {
        val body = if (method == "GET" || method == "DELETE") null else "{}".toRequestBody()
        return Request.Builder().url("https://loadwatch.test/$path").method(method, body).build()
    }

    private fun ok(request: Request) = response(request, 200, "OK")

    private fun unauthorized(request: Request) = response(request, 401, "Unauthorized")

    private fun response(request: Request, code: Int, message: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body("".toResponseBody())
            .build()

    private class TestAuthSession : AuthSession {
        override val signedInUserId = MutableStateFlow<String?>("user-1")
        var forcedRefreshes = 0

        override suspend fun idToken(forceRefresh: Boolean): String? {
            if (signedInUserId.value == null) return null
            if (forceRefresh) forcedRefreshes++
            return if (forceRefresh) "fresh-token" else "cached-token"
        }

        override suspend fun signInWithGoogleIdToken(googleIdToken: String) = Unit
        override suspend fun signOut() {
            signedInUserId.value = null
        }
    }
}
