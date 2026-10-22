package com.example.loadwatch.feature.schedule

import androidx.lifecycle.SavedStateHandle
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.fakes.MainDispatcherRule
import com.example.loadwatch.fakes.ScheduleEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.newViewModel(env: ScheduleEnvironment): ScheduleViewModel {
        val viewModel = ScheduleViewModel(SavedStateHandle(), env.settingsRepository, env.scheduleRepository, env.clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun ScheduleViewModel.ready() = uiState.value as ScheduleUiState.Ready

    @Test
    fun `it opens on seven days and fetches fourteen when the range changes`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.scheduleHandler = { _, _, _ -> emptyList() }
        val viewModel = newViewModel(env)
        runCurrent()

        assertEquals(7, viewModel.ready().days.size)
        assertEquals(Duration.ofDays(7), env.api.scheduleRequests.last().let { Duration.between(it.second, it.third) })

        viewModel.selectRange(ScheduleRange.FOURTEEN_DAYS)
        runCurrent()

        assertEquals(Duration.ofDays(14), env.api.scheduleRequests.last().let { Duration.between(it.second, it.third) })
        assertEquals(14, viewModel.ready().days.size)
        assertTrue(viewModel.ready().days.all { it.isLoaded })
    }

    @Test
    fun `windows are grouped by day and empty days say no scheduled outage`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        // Tomorrow 10:00 to 12:30 SAST
        val tomorrow = env.start.plus(Duration.ofHours(14))
        env.api.scheduleHandler = { _, _, _ -> listOf(env.dto("tomorrow", tomorrow, tomorrow.plus(Duration.ofMinutes(150)), stage = 3)) }
        val viewModel = newViewModel(env)
        runCurrent()

        val days = viewModel.ready().days
        assertTrue("Today has no outage", days[0].events.isEmpty() && days[0].isLoaded)
        assertEquals(listOf("tomorrow"), days[1].events.map { it.scheduleId })
        assertEquals(3, days[1].events.single().stage)
    }

    @Test
    fun `offline with an old cache shows it as stale with a notice`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.cache(emptyList(), age = Duration.ofHours(7))
        val viewModel = newViewModel(env)

        runCurrent()

        val state = viewModel.ready()
        assertTrue(state.isStale)
        assertEquals(DataFailure.OFFLINE, state.refreshFailure)
    }

    @Test
    fun `offline with nothing cached offers a labelled retry`() = runTest {
        val viewModel = newViewModel(ScheduleEnvironment(testScheduler))

        runCurrent()

        assertEquals(ScheduleUiState.Unavailable(DataFailure.OFFLINE, isRefreshing = false), viewModel.uiState.value)
    }
}
