package com.example.loadwatch.feature.history

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.LoggedViewModel
import com.example.loadwatch.core.common.logKindChanges
import com.example.loadwatch.data.history.HistoryCalendar
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.HistoryDay
import com.example.loadwatch.data.model.HistoryMonth
import com.example.loadwatch.data.repository.HistoryRepository
import com.example.loadwatch.data.repository.SettingsRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// S10's two ways of showing a month
enum class HistoryView {
    CALENDAR,
    LIST,
}

// One day in the month grid
data class CalendarCell(
    val date: LocalDate,
    val outageCount: Int,
    // 0 for no outage, up to 3 for six hours or more without power
    val level: Int,
    val hasReports: Boolean,
    val isFuture: Boolean,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object NoArea : HistoryUiState

    // The month has never been loaded and could not be fetched now; another month may still be cached
    data class Unavailable(
        val failure: DataFailure,
        val isRefreshing: Boolean,
        val month: YearMonth,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : HistoryUiState

    data class Ready(
        val area: Area,
        val month: YearMonth,
        val weeks: List<List<CalendarCell?>>,
        // Newest first, for the list view
        val days: List<HistoryDay>,
        val selectedDate: LocalDate?,
        val outageCount: Int,
        val minutesWithoutPower: Long,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
        val fetchedAt: Instant,
        val deviceNow: Instant,
        val isRefreshing: Boolean,
        // Set when the cached month is shown because a refresh failed
        val refreshFailure: DataFailure?,
    ) : HistoryUiState {
        val isEmpty: Boolean get() = days.isEmpty()

        // Null when the selected day had no outages or reports
        val selectedDay: HistoryDay? get() = selectedDate?.let { date -> days.firstOrNull { it.date == date } }
    }
}

private sealed interface ActiveArea {
    data object Loading : ActiveArea
    data object None : ActiveArea
    data class Selected(val area: Area) : ActiveArea
}

private data class RefreshStatus(val isRefreshing: Boolean = false, val failure: DataFailure? = null)

// S10 Outage History: the selected area's past outages and community reports as a month calendar or a list, from
// Room first and refreshed from the API. Month, view and selected day survive rotation and process death.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val clock: Clock,
) : LoggedViewModel() {

    private val refreshStatus = MutableStateFlow(RefreshStatus())

    // A saved value that cannot be read, or lies outside the range, falls back instead of failing
    val month: StateFlow<YearMonth> = savedStateHandle.getStateFlow<String?>(MONTH_KEY, null)
        .map { saved -> HistoryCalendar.parseMonth(saved)?.let { HistoryCalendar.clamp(it, clock) } ?: HistoryCalendar.currentMonth(clock) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryCalendar.currentMonth(clock))

    val view: StateFlow<HistoryView> = savedStateHandle.getStateFlow(VIEW_KEY, HistoryView.CALENDAR.name)
        .map { saved -> HistoryView.entries.firstOrNull { it.name == saved } ?: HistoryView.CALENDAR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryView.CALENDAR)

    private val selectedDate: Flow<LocalDate?> = savedStateHandle.getStateFlow<String?>(DAY_KEY, null)
        .map { saved -> saved?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }

    private val activeArea: StateFlow<ActiveArea> = settingsRepository.observeDefaultArea()
        .map { area -> if (area == null) ActiveArea.None else ActiveArea.Selected(area) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActiveArea.Loading)

    private val areaId: Flow<String?> = activeArea.map { (it as? ActiveArea.Selected)?.area?.areaId }.distinctUntilChanged()

    private val history: Flow<HistoryMonth?> = combine(areaId, month) { id, month -> id to month }
        .distinctUntilChanged()
        .flatMapLatest { (id, month) -> if (id == null) flowOf(null) else historyRepository.observeMonth(id, month) }

    val uiState: StateFlow<HistoryUiState> =
        combine(activeArea, history, month, selectedDate, refreshStatus) { area, history, month, selected, status ->
            toUiState(area, history, month, selected, status)
        }.logKindChanges("History")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    init {
        // Fetch on open, and whenever the area or the month changes
        viewModelScope.launch {
            combine(areaId, month) { id, month -> id to month }.distinctUntilChanged().collectLatest { (id, month) ->
                if (id != null) refresh(id, month)
            }
        }
    }

    fun showPreviousMonth() {
        val previous = month.value.minusMonths(1)
        if (previous.isBefore(HistoryCalendar.earliestMonth(clock))) return
        showMonth(previous)
    }

    fun showNextMonth() {
        val next = month.value.plusMonths(1)
        if (next.isAfter(HistoryCalendar.currentMonth(clock))) return
        showMonth(next)
    }

    fun selectView(newView: HistoryView) {
        Log.d(LogTags.STATE, "History view changed to $newView")
        savedStateHandle[VIEW_KEY] = newView.name
    }

    // Days outside the month or still to come cannot be picked
    fun selectDay(date: LocalDate) {
        if (YearMonth.from(date) != month.value || date.isAfter(HistoryCalendar.today(clock))) return
        savedStateHandle[DAY_KEY] = date.toString()
    }

    fun refresh() {
        val id = (activeArea.value as? ActiveArea.Selected)?.area?.areaId ?: return
        viewModelScope.launch { refresh(id, month.value) }
    }

    private fun showMonth(newMonth: YearMonth) {
        Log.d(LogTags.STATE, "History month changed to $newMonth")
        savedStateHandle[DAY_KEY] = null
        savedStateHandle[MONTH_KEY] = newMonth.toString()
    }

    private suspend fun refresh(areaId: String, month: YearMonth) {
        if (refreshStatus.value.isRefreshing) return
        refreshStatus.value = RefreshStatus(isRefreshing = true)
        var failure: DataFailure? = null
        try {
            failure = historyRepository.refresh(areaId, month)
            if (failure != null) Log.w(LogTags.API, "History refresh for $month failed ($failure); showing the cached month if there is one")
        } finally {
            // Also runs when switching month cancels this refresh, so the next one is not blocked
            refreshStatus.value = RefreshStatus(isRefreshing = false, failure = failure)
        }
    }

    private fun toUiState(
        area: ActiveArea,
        history: HistoryMonth?,
        month: YearMonth,
        selected: LocalDate?,
        status: RefreshStatus,
    ): HistoryUiState {
        if (area == ActiveArea.Loading) return HistoryUiState.Loading
        if (area !is ActiveArea.Selected) return HistoryUiState.NoArea
        val canGoBack = month.isAfter(HistoryCalendar.earliestMonth(clock))
        val canGoForward = month.isBefore(HistoryCalendar.currentMonth(clock))
        if (status.failure == DataFailure.NOT_FOUND) {
            return HistoryUiState.Unavailable(DataFailure.NOT_FOUND, status.isRefreshing, month, canGoBack, canGoForward)
        }

        val fetchedAt = history?.fetchedAt
        if (history == null || history.month != month || fetchedAt == null) {
            return status.failure?.let { HistoryUiState.Unavailable(it, status.isRefreshing, month, canGoBack, canGoForward) }
                ?: HistoryUiState.Loading
        }

        val now = clock.instant()
        val today = HistoryCalendar.today(clock)
        val days = HistoryCalendar.groupByDay(history.outages, history.reports)
        val daysByDate = days.associateBy { it.date }
        val weeks = HistoryCalendar.weeks(month).map { week ->
            week.map { date ->
                date?.let {
                    val day = daysByDate[it]
                    val outages = day?.outages.orEmpty()
                    CalendarCell(
                        date = it,
                        outageCount = outages.size,
                        level = HistoryCalendar.level(HistoryCalendar.minutesWithoutPower(outages, now)),
                        hasReports = !day?.reports.isNullOrEmpty(),
                        isFuture = it.isAfter(today),
                    )
                }
            }
        }
        // Today is picked by default in the current month, so the calendar opens on something useful
        val selectedDate = selected?.takeIf { YearMonth.from(it) == month } ?: today.takeIf { YearMonth.from(it) == month }

        return HistoryUiState.Ready(
            area = area.area,
            month = month,
            weeks = weeks,
            days = days.reversed(),
            selectedDate = selectedDate,
            outageCount = history.outages.size,
            minutesWithoutPower = HistoryCalendar.minutesWithoutPower(history.outages, now),
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            fetchedAt = fetchedAt,
            deviceNow = now,
            isRefreshing = status.isRefreshing,
            refreshFailure = status.failure,
        )
    }

    private companion object {
        const val MONTH_KEY = "month"
        const val VIEW_KEY = "view"
        const val DAY_KEY = "day"
    }
}
