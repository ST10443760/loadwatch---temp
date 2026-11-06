package com.example.loadwatch.data.repository

import com.example.loadwatch.core.network.dto.DeviceRegistrationDto
import com.example.loadwatch.data.remote.PushRemoteDataSource
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakePushMessaging
import com.example.loadwatch.fakes.FakePushPreferences
import com.example.loadwatch.fakes.httpError
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// The phone follows exactly one area topic: the selected area's, and none once signed out
class PushRepositoryTest {

    private val api = FakeLoadWatchApi()
    private val messaging = FakePushMessaging()
    private val preferences = FakePushPreferences()

    private fun repository(userId: String? = "user-1") =
        PushRepository(FakeAuthSession(userId), PushRemoteDataSource(api, testJson), messaging, preferences)

    @Test
    fun `choosing an area subscribes to its topic`() = runTest {
        repository().followArea("eth-berea")

        assertEquals(setOf("area-eth-berea"), messaging.subscribed)
        assertEquals("area-eth-berea", preferences.subscribedTopic)
    }

    @Test
    fun `changing area leaves the old topic before joining the new one`() = runTest {
        val repository = repository()
        repository.followArea("eth-berea")

        repository.followArea("eth-kloof")

        assertEquals(setOf("area-eth-kloof"), messaging.subscribed)
        assertEquals(listOf("subscribe area-eth-berea", "unsubscribe area-eth-berea", "subscribe area-eth-kloof"), messaging.calls)
    }

    @Test
    fun `signing out leaves the topic`() = runTest {
        val repository = repository()
        repository.followArea("eth-berea")

        repository.followArea(null)

        assertTrue(messaging.subscribed.isEmpty())
        assertNull(preferences.subscribedTopic)
    }

    @Test
    fun `the topic remembered from a previous launch is the one that is left`() = runTest {
        preferences.subscribedTopic = "area-eth-berea"

        repository().followArea("eth-umhlanga")

        assertEquals(listOf("unsubscribe area-eth-berea", "subscribe area-eth-umhlanga"), messaging.calls)
    }

    @Test
    fun `the same area twice makes no calls`() = runTest {
        preferences.subscribedTopic = "area-eth-berea"

        repository().followArea("eth-berea")

        assertTrue(messaging.calls.isEmpty())
    }

    @Test
    fun `a topic change that fails offline is tried again next time`() = runTest {
        val repository = repository()
        messaging.offline = true
        repository.followArea("eth-berea")
        assertNull("Nothing recorded for the failed step", preferences.subscribedTopic)

        messaging.offline = false
        repository.followArea("eth-berea")

        assertEquals("area-eth-berea", preferences.subscribedTopic)
    }

    @Test
    fun `the token is sent to the API when signed in`() = runTest {
        api.registerDeviceHandler = { request ->
            DeviceRegistrationDto(request.fcmToken, "user-1", request.platform, Instant.parse("2026-11-05T10:00:00Z"))
        }

        assertTrue(repository().registerDevice())
        assertEquals(1, api.callCount("registerDevice"))
    }

    @Test
    fun `the token is not sent when signed out or offline`() = runTest {
        assertFalse(repository(userId = null).registerDevice())
        assertEquals(0, api.callCount("registerDevice"))

        assertFalse("The fake API is offline by default", repository().registerDevice(token = "new-token"))
    }

    @Test
    fun `a registration refused with 401 is sent again on the next sign-in or launch`() = runTest {
        api.registerDeviceHandler = { httpError(401, """{"status":401,"title":"Not signed in"}""") }
        assertFalse(repository().registerDevice())

        api.registerDeviceHandler = { request ->
            DeviceRegistrationDto(request.fcmToken, "user-1", request.platform, Instant.parse("2026-11-05T10:00:00Z"))
        }
        assertTrue(repository().registerDevice())
        assertEquals(2, api.callCount("registerDevice"))
    }
}
