package com.example.loadwatch.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.repository.AccuracyScore
import com.example.loadwatch.data.repository.AreaSchedule
import com.example.loadwatch.data.repository.ScheduleRefreshResult
import com.example.loadwatch.data.repository.ScheduleRepository
import com.example.loadwatch.data.repository.SettingsRepository
import com.example.loadwatch.data.schedule.Countdown
import com.example.loadwatch.data.schedule.CountdownCalculator
import com.example.loadwatch.data.schedule.ScheduleFreshness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    // Signed in without a default area, which onboarding normally prevents
    data object NoArea : HomeUiState

    // Nothing cached to fall back on, or the area is unknown
    data class Unavailable(val failure: DataFailure, val isRefreshing: Boolean) : HomeUiState

    data class Ready(
        val area: Area,
        val countdown: Countdown,
        val currentStage: Int?,
        val accuracy: AccuracyScore?,
        val lastRefreshedAt: Instant,
        val isStale: Boolean,
        val deviceNow: Instant,
        val isRefreshing: Boolean,
        // Set when the last refresh failed and the screen is showing cached data
        val refreshFailure: DataFailure?,
    ) : HomeUiState
}

private sealed interface ActiveArea {
    data object Loading : ActiveArea
    data object None : ActiveArea
    data class Selected(val areaId: String) : ActiveArea
}

private data class RefreshStatus(val isRefreshing: Boolean = false, val failure: DataFailure? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {

    private val calculator = CountdownCalculator()
    private val refreshStatus = MutableStateFlow(RefreshStatus())
    private val startOfToday: Instant = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()

    private val activeArea: StateFlow<ActiveArea> = settingsRepository.observeDefaultArea()
        .map { area -> if (area == null) ActiveArea.None else ActiveArea.Selected(area.areaId) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveArea.Loading)

    private val schedule: Flow<AreaSchedule?> = activeArea.flatMapLatest { area ->
        if (area is ActiveArea.Selected) scheduleRepository.observeAreaSchedule(area.areaId, startOfToday) else flowOf(null)
    }

    private var lastCountdownKey: Triple<String, SupplyState, Instant?>? = null

    // Room first, then the network: the cached schedule is combined with a local one-second tick, so the
    // countdown runs offline and rolls over to the next window on its own (FR-COUNT-01 to 03). While nothing
    // collects this (the screen is not visible) the ticker stops.
    val uiState: StateFlow<HomeUiState> =
        combine(activeArea, schedule, refreshStatus, secondTicks(clock)) { area, cached, status, now ->
            toUiState(area, cached, status, now)
        }
            .onEach(::logRollover)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        // Refresh when Home opens and whenever the active area changes
        viewModelScope.launch {
            activeArea.collectLatest { area ->
                if (area is ActiveArea.Selected) refresh(area.areaId)
            }
        }
    }

    fun refresh() {
        val area = activeArea.value as? ActiveArea.Selected ?: return
        viewModelScope.launch { refresh(area.areaId) }
    }

    private suspend fun refresh(areaId: String) {
        if (refreshStatus.value.isRefreshing) return
        refreshStatus.update { it.copy(isRefreshing = true) }
        val result = scheduleRepository.refresh(areaId, ScheduleRepository.DEFAULT_DAYS)
        val failure = (result as? ScheduleRefreshResult.Failed)?.failure
        if (failure != null) Log.w(LogTags.API, "Home refresh failed ($failure); showing cached data if there is any")
        refreshStatus.value = RefreshStatus(isRefreshing = false, failure = failure)
    }

    private fun toUiState(area: ActiveArea, cached: AreaSchedule?, status: RefreshStatus, now: Instant): HomeUiState {
        if (area == ActiveArea.Loading) return HomeUiState.Loading
        if (area == ActiveArea.None) return HomeUiState.NoArea
        if (status.failure == DataFailure.NOT_FOUND) return HomeUiState.Unavailable(DataFailure.NOT_FOUND, status.isRefreshing)

        val lastRefreshedAt = cached?.lastRefreshedAt
        if (cached?.area == null || lastRefreshedAt == null) {
            // Never refreshed: there is no cache to show, so either wait for the first refresh or offer Retry
            return status.failure?.let { HomeUiState.Unavailable(it, status.isRefreshing) } ?: HomeUiState.Loading
        }

        // The API's next event covers a window that may lie beyond the cached days
        val events = cached.apiNextEvent
            ?.takeIf { next -> cached.events.none { it.startAt == next.startAt } }
            ?.let { cached.events + it }
            ?: cached.events

        return HomeUiState.Ready(
            area = cached.area,
            countdown = calculator.calculate(events, now, cached.clockOffset),
            currentStage = cached.currentStage,
            accuracy = cached.accuracy,
            lastRefreshedAt = lastRefreshedAt,
            isStale = ScheduleFreshness.isStale(lastRefreshedAt, now),
            deviceNow = now,
            isRefreshing = status.isRefreshing,
            refreshFailure = status.failure,
        )
    }

    private fun logRollover(state: HomeUiState) {
        val ready = state as? HomeUiState.Ready ?: return
        val key = Triple(ready.area.areaId, ready.countdown.state, ready.countdown.target)
        val previous = lastCountdownKey
        if (previous != null && previous.first == key.first && previous != key) {
            Log.i(
                LogTags.COUNTDOWN,
                "Rolled over: ${previous.second} -> ${key.second}, now counting to ${key.third ?: "no upcoming window"}",
            )
        }
        lastCountdownKey = key
    }
}
