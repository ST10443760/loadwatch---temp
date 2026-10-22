package com.example.loadwatch.fakes

import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.database.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

val SAST: ZoneId = ZoneId.of("Africa/Johannesburg")

// A clock that moves with the coroutine test scheduler's virtual time, so tickers and countdowns can be
// advanced deterministically with advanceTimeBy
@OptIn(ExperimentalCoroutinesApi::class)
class TestClock(
    private val start: Instant,
    private val scheduler: TestCoroutineScheduler,
    private val zone: ZoneId = SAST,
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(start, scheduler, zone)
    override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
}

// Runs viewModelScope on the test scheduler
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeAuthSession(userId: String? = "user-1", private val email: String? = "thandi@example.com") : AuthSession {
    override val signedInUserId = MutableStateFlow(userId)
    var signOutCalls = 0

    override fun currentUserEmail(): String? = email
    override suspend fun idToken(forceRefresh: Boolean): String? = signedInUserId.value?.let { "token" }
    override suspend fun signInWithGoogleIdToken(googleIdToken: String) = Unit
    override suspend fun signOut() {
        signOutCalls++
        signedInUserId.value = null
    }
}

// Records whether writes happen inside a transaction
class FakeTransactionRunner : TransactionRunner {
    var transactionCount = 0
    var inTransaction = false
        private set

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        transactionCount++
        inTransaction = true
        try {
            return block()
        } finally {
            inTransaction = false
        }
    }
}
