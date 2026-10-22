package com.example.loadwatch.feature.home

import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.fakes.MainDispatcherRule
import com.example.loadwatch.fakes.ScheduleEnvironment
import com.example.loadwatch.fakes.httpError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.newViewModel(env: ScheduleEnvironment): HomeViewModel {
        val viewModel = HomeViewModel(env.settingsRepository, env.scheduleRepository, env.clock)
        // The screen collects uiState while visible; the countdown only ticks while it does
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun HomeViewModel.ready() = uiState.value as HomeUiState.Ready

    @Test
    fun `offline with a cached schedule shows the cache with a notice, never a blank screen`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        val start = env.start.plus(Duration.ofHours(2))
        env.cache(listOf(env.event("evening", start, start.plus(Duration.ofMinutes(150)))))
        val viewModel = newViewModel(env)

        runCurrent()

        val state = viewModel.ready()
        assertEquals(DataFailure.OFFLINE, state.refreshFailure)
        assertEquals(SupplyState.ON, state.countdown.state)
        assertEquals(start, state.countdown.target)
        assertEquals("Berea", state.area.name)
    }

    @Test
    fun `offline with nothing cached offers a labelled retry`() = runTest {
        val viewModel = newViewModel(ScheduleEnvironment(testScheduler))

        runCurrent()

        assertEquals(HomeUiState.Unavailable(DataFailure.OFFLINE, isRefreshing = false), viewModel.uiState.value)
    }

    @Test
    fun `an unknown area is reported as not found`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.scheduleHandler = { _, _, _ -> httpError(404, """{"status":404,"title":"Area not found"}""") }
        val viewModel = newViewModel(env)

        runCurrent()

        assertEquals(HomeUiState.Unavailable(DataFailure.NOT_FOUND, isRefreshing = false), viewModel.uiState.value)
    }

    @Test
    fun `a successful refresh replaces the notice with fresh data`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        val start = env.start.plus(Duration.ofHours(3))
        env.api.scheduleHandler = { _, _, _ -> listOf(env.dto("fresh", start, start.plus(Duration.ofMinutes(150)), stage = 4)) }
        val viewModel = newViewModel(env)

        runCurrent()

        val state = viewModel.ready()
        assertNull(state.refreshFailure)
        assertFalse(state.isStale)
        assertEquals(env.start, state.lastRefreshedAt)
        assertEquals(4, state.countdown.nextEvent?.stage)
    }

    @Test
    fun `the countdown ticks locally and rolls over at each boundary without the network`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        val windowStart = env.start.plusSeconds(10)
        val windowEnd = env.start.plusSeconds(20)
        env.cache(listOf(env.event("short", windowStart, windowEnd)))
        val viewModel = newViewModel(env)
        runCurrent()
        val callsAfterOpening = env.api.calls.size

        assertEquals(SupplyState.ON, viewModel.ready().countdown.state)
        assertEquals(Duration.ofSeconds(10), viewModel.ready().countdown.remaining)

        advanceTimeBy(4_000)
        runCurrent()
        assertEquals("It ticks every second", Duration.ofSeconds(6), viewModel.ready().countdown.remaining)

        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(SupplyState.OFF, viewModel.ready().countdown.state)
        assertEquals(windowEnd, viewModel.ready().countdown.target)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(SupplyState.ON, viewModel.ready().countdown.state)
        assertNull(viewModel.ready().countdown.target)

        assertEquals("Ticking made no API calls", callsAfterOpening, env.api.calls.size)
    }

    @Test
    fun `a cache older than six hours is flagged as stale`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.cache(emptyList(), age = Duration.ofHours(7))
        val viewModel = newViewModel(env)

        runCurrent()

        assertTrue(viewModel.ready().isStale)
    }
}
