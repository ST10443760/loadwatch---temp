package com.example.loadwatch.feature.schedule

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.repository.AreaSchedule
import com.example.loadwatch.data.repository.ScheduleRefreshResult
import com.example.loadwatch.data.repository.ScheduleRepository
import com.example.loadwatch.data.repository.SettingsRepository
import com.example.loadwatch.data.schedule.ScheduleDay
import com.example.loadwatch.data.schedule.ScheduleDays
import com.example.loadwatch.data.schedule.ScheduleFreshness
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

// The S06 range selector
enum class ScheduleRange(val days: Int) {
    THREE_DAYS(3),
    SEVEN_DAYS(7),
    FOURTEEN_DAYS(14),
}

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data object NoArea : ScheduleUiState
    data class Unavailable(val failure: DataFailure, val isRefreshing: Boolean) : ScheduleUiState

    data class Ready(
        val area: Area,
        val days: List<ScheduleDay>,
        val today: LocalDate,
        val lastRefreshedAt: Instant,
        val isStale: Boolean,
        val deviceNow: Instant,
        val isRefreshing: Boolean,
        val refreshFailure: DataFailure?,
    ) : ScheduleUiState
}

private sealed interface ActiveArea {
    data object Loading : ActiveArea
    data object None : ActiveArea
    data class Selected(val areaId: String) : ActiveArea
}

private data class RefreshStatus(val isRefreshing: Boolean = false, val failure: DataFailure? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {

    val range: StateFlow<ScheduleRange> = savedStateHandle.getStateFlow(RANGE_KEY, ScheduleRange.SEVEN_DAYS)

    private val refreshStatus = MutableStateFlow(RefreshStatus())
    private val startOfToday: Instant = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()

    private val activeArea: StateFlow<ActiveArea> = settingsRepository.observeDefaultArea()
        .map { area -> if (area == null) ActiveArea.None else ActiveArea.Selected(area.areaId) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveArea.Loading)

    private val schedule: Flow<AreaSchedule?> = activeArea.flatMapLatest { area ->
        if (area is ActiveArea.Selected) scheduleRepository.observeAreaSchedule(area.areaId, startOfToday) else flowOf(null)
    }

    // Keeps "updated N minutes ago" and the stale badge current without ticking every second
    private val minuteTicks: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(60_000)
        }
    }

    // Opens from Room and updates when the network answers
    val uiState: StateFlow<ScheduleUiState> =
        combine(activeArea, schedule, range, refreshStatus, minuteTicks) { area, cached, range, status, now ->
            toUiState(area, cached, range, status, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState.Loading)

    init {
        // Refresh on open, when the area changes and when a different range is chosen
        viewModelScope.launch {
            combine(activeArea, range) { area, range -> area to range }.collectLatest { (area, range) ->
                if (area is ActiveArea.Selected) refresh(area.areaId, range)
            }
        }
    }

    fun selectRange(newRange: ScheduleRange) {
        savedStateHandle[RANGE_KEY] = newRange
    }

    fun refresh() {
        val area = activeArea.value as? ActiveArea.Selected ?: return
        viewModelScope.launch { refresh(area.areaId, range.value) }
    }

    private suspend fun refresh(areaId: String, range: ScheduleRange) {
        if (refreshStatus.value.isRefreshing) return
        refreshStatus.update { it.copy(isRefreshing = true) }
        val result = scheduleRepository.refresh(areaId, range.days)
        val failure = (result as? ScheduleRefreshResult.Failed)?.failure
        if (failure != null) Log.w(LogTags.API, "Schedule refresh failed ($failure); showing cached data if there is any")
        refreshStatus.value = RefreshStatus(isRefreshing = false, failure = failure)
    }

    private fun toUiState(
        area: ActiveArea,
        cached: AreaSchedule?,
        range: ScheduleRange,
        status: RefreshStatus,
        now: Instant,
    ): ScheduleUiState {
        if (area == ActiveArea.Loading) return ScheduleUiState.Loading
        if (area == ActiveArea.None) return ScheduleUiState.NoArea
        if (status.failure == DataFailure.NOT_FOUND) return ScheduleUiState.Unavailable(DataFailure.NOT_FOUND, status.isRefreshing)

        val lastRefreshedAt = cached?.lastRefreshedAt
        if (cached?.area == null || lastRefreshedAt == null) {
            return status.failure?.let { ScheduleUiState.Unavailable(it, status.isRefreshing) } ?: ScheduleUiState.Loading
        }

        val today = LocalDate.ofInstant(now, clock.zone)
        return ScheduleUiState.Ready(
            area = cached.area,
            days = ScheduleDays.group(cached.events, today, range.days, clock.zone, cached.loadedUntil),
            today = today,
            lastRefreshedAt = lastRefreshedAt,
            isStale = ScheduleFreshness.isStale(lastRefreshedAt, now),
            deviceNow = now,
            isRefreshing = status.isRefreshing,
            refreshFailure = status.failure,
        )
    }

    private companion object {
        const val RANGE_KEY = "range"
    }
}
