package com.example.loadwatch.feature.history

import androidx.lifecycle.SavedStateHandle
import com.example.loadwatch.core.network.dto.AreaHistoryDto
import com.example.loadwatch.core.network.dto.HistoryDayDto
import com.example.loadwatch.core.network.dto.HistoryOutageDto
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.remote.HistoryRemoteDataSource
import com.example.loadwatch.data.repository.HistoryRepository
import com.example.loadwatch.fakes.FakeHistoryDao
import com.example.loadwatch.fakes.MainDispatcherRule
import com.example.loadwatch.fakes.ScheduleEnvironment
import com.example.loadwatch.fakes.offline
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// The environment's clock reads 20:00 on 22 October 2026 in Johannesburg
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val october = YearMonth.of(2026, 10)

    private fun TestScope.newViewModel(env: ScheduleEnvironment, saved: SavedStateHandle = SavedStateHandle()): HistoryViewModel {
        val repository = HistoryRepository(HistoryRemoteDataSource(env.api, testJson), FakeHistoryDao(), env.clock)
        val viewModel = HistoryViewModel(saved, env.settingsRepository, repository, env.clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun HistoryViewModel.ready() = uiState.value as HistoryUiState.Ready

    @Test
    fun `it opens on the current month with today selected and shades days by outage`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month -> history(areaId, month) }
        val viewModel = newViewModel(env)
        runCurrent()

        val state = viewModel.ready()
        assertEquals(october, state.month)
        assertEquals(LocalDate.of(2026, 10, 22), state.selectedDate)
        assertEquals(listOf(ScheduleEnvironment.AREA to "2026-10"), env.api.historyRequests)
        val cells = state.weeks.flatten().filterNotNull().associateBy { it.date }
        assertEquals(1, cells.getValue(LocalDate.of(2026, 10, 20)).outageCount)
        assertEquals(1, cells.getValue(LocalDate.of(2026, 10, 20)).level)
        assertEquals(0, cells.getValue(LocalDate.of(2026, 10, 21)).level)
        assertTrue(cells.getValue(LocalDate.of(2026, 10, 25)).isFuture)
        assertEquals(1, state.outageCount)
        assertEquals(150L, state.minutesWithoutPower)
    }

    @Test
    fun `the list view shows the newest day first`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month ->
            history(areaId, month, "2026-10-02T06:00:00Z" to "2026-10-02T08:30:00Z", "2026-10-20T06:00:00Z" to "2026-10-20T08:30:00Z")
        }
        val viewModel = newViewModel(env)
        runCurrent()

        viewModel.selectView(HistoryView.LIST)
        runCurrent()

        assertEquals(HistoryView.LIST, viewModel.view.value)
        assertEquals(listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 2)), viewModel.ready().days.map { it.date })
    }

    @Test
    fun `going back a month fetches it, and going forward stops at the current month`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month -> history(areaId, month) }
        val viewModel = newViewModel(env)
        runCurrent()

        assertFalse(viewModel.ready().canGoForward)
        viewModel.showNextMonth()
        assertEquals(october, viewModel.month.value)

        viewModel.showPreviousMonth()
        runCurrent()

        assertEquals(october.minusMonths(1), viewModel.ready().month)
        assertEquals("2026-09", env.api.historyRequests.last().second)
        assertTrue(viewModel.ready().canGoForward)
        assertNull("No day is picked by default in a past month", viewModel.ready().selectedDate)
    }

    @Test
    fun `future days and days in another month cannot be selected`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month -> history(areaId, month) }
        val viewModel = newViewModel(env)
        runCurrent()

        viewModel.selectDay(LocalDate.of(2026, 10, 25))
        viewModel.selectDay(LocalDate.of(2026, 9, 30))
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 22), viewModel.ready().selectedDate)

        viewModel.selectDay(LocalDate.of(2026, 10, 20))
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 20), viewModel.ready().selectedDay?.date)
    }

    @Test
    fun `a saved month that cannot be read or is too far ahead falls back safely`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month -> history(areaId, month) }

        val unreadable = newViewModel(env, SavedStateHandle(mapOf("month" to "not-a-month", "view" to "SIDEWAYS", "day" to "32-13")))
        runCurrent()
        assertEquals(october, unreadable.ready().month)
        assertEquals(HistoryView.CALENDAR, unreadable.view.value)

        val future = newViewModel(env, SavedStateHandle(mapOf("month" to "2030-01")))
        runCurrent()
        assertEquals(october, future.ready().month)
    }

    @Test
    fun `offline a month never loaded is unavailable but can still be navigated`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { _, _ -> offline() }
        val viewModel = newViewModel(env)
        runCurrent()

        val state = viewModel.uiState.value as HistoryUiState.Unavailable
        assertEquals(DataFailure.OFFLINE, state.failure)
        assertTrue(state.canGoBack)
    }

    @Test
    fun `offline a cached month is still shown with the failure`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month -> history(areaId, month) }
        val viewModel = newViewModel(env)
        runCurrent()

        env.api.historyHandler = { _, _ -> offline() }
        viewModel.refresh()
        runCurrent()

        assertEquals(DataFailure.OFFLINE, viewModel.ready().refreshFailure)
        assertEquals(1, viewModel.ready().outageCount)
    }

    @Test
    fun `an empty month is ready and says so`() = runTest {
        val env = ScheduleEnvironment(testScheduler)
        env.api.historyHandler = { areaId, month -> AreaHistoryDto(areaId, month, emptyList()) }
        val viewModel = newViewModel(env)
        runCurrent()

        assertTrue(viewModel.ready().isEmpty)
    }

    // One outage on 20 October 08:00 to 10:30 in Johannesburg unless other windows are given
    private fun history(areaId: String, month: String, vararg windows: Pair<String, String>): AreaHistoryDto {
        val outages = windows.ifEmpty { arrayOf("2026-10-20T06:00:00Z" to "2026-10-20T08:30:00Z") }.mapIndexed { index, (start, end) ->
            HistoryOutageDto("o$index", 2, Instant.parse(start), Instant.parse(end), 0)
        }
        return AreaHistoryDto(areaId, month, outages.map { HistoryDayDto(it.startAt.toString().take(10), listOf(it), emptyList()) })
    }
}
