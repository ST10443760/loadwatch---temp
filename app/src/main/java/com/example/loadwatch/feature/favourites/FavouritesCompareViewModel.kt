package com.example.loadwatch.feature.favourites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.secondTicks
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.repository.AreaSchedule
import com.example.loadwatch.data.repository.FavouriteResult
import com.example.loadwatch.data.repository.FavouritesRepository
import com.example.loadwatch.data.repository.ScheduleRefreshResult
import com.example.loadwatch.data.repository.ScheduleRepository
import com.example.loadwatch.data.schedule.Countdown
import com.example.loadwatch.data.schedule.CountdownCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

// One favourite on the compare screen, with its live countdown from the cached schedule
data class CompareCard(
    val favourite: Favourite,
    // Null until the area's schedule has been cached at least once
    val countdown: Countdown?,
)

sealed interface CompareMessage {
    data class Removed(val areaName: String) : CompareMessage
    data class Relabelled(val areaName: String) : CompareMessage
    data object LabelTooLong : CompareMessage
    data class Failed(val failure: DataFailure) : CompareMessage
}

data class CompareEditState(
    val editing: Favourite? = null,
    val confirmingRemoval: Favourite? = null,
    val isSaving: Boolean = false,
    val message: CompareMessage? = null,
)

data class CompareUiState(
    val isLoading: Boolean = true,
    val cards: List<CompareCard> = emptyList(),
    val deviceNow: Instant = Instant.EPOCH,
    val isRefreshing: Boolean = false,
    // Set when the favourites list or any schedule could not be refreshed; the screen shows cached data
    val refreshFailure: DataFailure? = null,
    val edit: CompareEditState = CompareEditState(),
)

private data class RefreshStatus(val isRefreshing: Boolean = false, val failure: DataFailure? = null)

// S08: every favourite side by side with its next event and a live countdown (FR-FAV-01 to 03)
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavouritesCompareViewModel @Inject constructor(
    private val favouritesRepository: FavouritesRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clock: Clock,
) : ViewModel() {

    private val calculator = CountdownCalculator()
    private val startOfToday: Instant = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
    private val refreshStatus = MutableStateFlow(RefreshStatus())
    private val editState = MutableStateFlow(CompareEditState())
    private val refreshedAreaIds = mutableSetOf<String>()

    // Null until Room has answered, so an empty list really means no favourites
    private val favourites: StateFlow<List<Favourite>?> = favouritesRepository.observeFavourites()
        .map<List<Favourite>, List<Favourite>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val schedules: Flow<Map<String, AreaSchedule>> = favourites.flatMapLatest { list ->
        if (list.isNullOrEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(list.map { favourite -> scheduleRepository.observeAreaSchedule(favourite.area.areaId, startOfToday) }) { all ->
                all.associateBy { it.area?.areaId.orEmpty() }
            }
        }
    }

    val uiState: StateFlow<CompareUiState> =
        combine(favourites, schedules, secondTicks(clock), refreshStatus, editState) { list, cached, now, status, edit ->
            CompareUiState(
                isLoading = list == null,
                cards = list.orEmpty().map { favourite -> CompareCard(favourite, cached[favourite.area.areaId]?.countdownAt(now)) },
                deviceNow = now,
                isRefreshing = status.isRefreshing,
                refreshFailure = status.failure,
                edit = edit,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    init {
        refresh()
        // A favourite added elsewhere gets its schedule fetched the first time it appears here
        viewModelScope.launch {
            favourites.filterNotNull().collect { list ->
                val newIds = list.map { it.area.areaId }.filter { it !in refreshedAreaIds }
                if (newIds.isNotEmpty() && !refreshStatus.value.isRefreshing) refreshSchedules(newIds)
            }
        }
    }

    fun refresh() {
        if (refreshStatus.value.isRefreshing) return
        viewModelScope.launch {
            refreshStatus.value = RefreshStatus(isRefreshing = true)
            val favouritesFailure = favouritesRepository.refresh()
            val areaIds = favourites.value.orEmpty().map { it.area.areaId }
            val scheduleFailure = refreshScheduleResults(areaIds)
            val failure = favouritesFailure ?: scheduleFailure
            if (failure != null) Log.w(LogTags.API, "Compare refresh incomplete ($failure); showing cached data")
            refreshStatus.value = RefreshStatus(isRefreshing = false, failure = failure)
        }
    }

    fun requestRemove(favourite: Favourite) = editState.update { it.copy(confirmingRemoval = favourite) }

    fun cancelRemove() = editState.update { it.copy(confirmingRemoval = null) }

    fun confirmRemove() {
        val favourite = editState.value.confirmingRemoval ?: return
        editState.update { it.copy(confirmingRemoval = null, isSaving = true) }
        viewModelScope.launch {
            val result = favouritesRepository.remove(favourite.area.areaId)
            Log.i(LogTags.API, "Remove favourite ${favourite.area.areaId}: ${result.javaClass.simpleName}")
            editState.update {
                it.copy(
                    isSaving = false,
                    message = if (result == FavouriteResult.Saved) CompareMessage.Removed(favourite.area.name) else result.toMessage(),
                )
            }
        }
    }

    fun startEditing(favourite: Favourite) = editState.update { it.copy(editing = favourite, message = null) }

    fun cancelEditing() = editState.update { it.copy(editing = null, isSaving = false) }

    fun saveLabel(label: String) {
        val favourite = editState.value.editing ?: return
        if (editState.value.isSaving) return
        editState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = favouritesRepository.relabel(favourite.area.areaId, label)
            Log.i(LogTags.API, "Relabel favourite ${favourite.area.areaId}: ${result.javaClass.simpleName}")
            editState.update {
                CompareEditState(
                    // Keep the dialog open only when the label itself needs fixing
                    editing = if (result == FavouriteResult.LabelTooLong) favourite else null,
                    message = if (result == FavouriteResult.Saved) CompareMessage.Relabelled(favourite.area.name) else result.toMessage(),
                )
            }
        }
    }

    fun dismissMessage() = editState.update { it.copy(message = null) }

    private fun refreshSchedules(areaIds: List<String>) {
        viewModelScope.launch {
            refreshScheduleResults(areaIds)?.let { failure -> refreshStatus.update { it.copy(failure = failure) } }
        }
    }

    // Refreshes each area's schedule in parallel; returns the first failure, if any
    private suspend fun refreshScheduleResults(areaIds: List<String>): DataFailure? = coroutineScope {
        refreshedAreaIds += areaIds
        areaIds.map { areaId -> async { scheduleRepository.refresh(areaId) } }
            .awaitAll()
            .filterIsInstance<ScheduleRefreshResult.Failed>()
            .firstOrNull()
            ?.failure
    }

    private fun AreaSchedule.countdownAt(now: Instant): Countdown? {
        if (lastRefreshedAt == null) return null
        // The API's next event covers a window that may lie beyond the cached days
        val allEvents = apiNextEvent
            ?.takeIf { next -> events.none { it.startAt == next.startAt } }
            ?.let { events + it }
            ?: events
        return calculator.calculate(allEvents, now, clockOffset)
    }

    private fun FavouriteResult.toMessage(): CompareMessage = when (this) {
        FavouriteResult.LabelTooLong -> CompareMessage.LabelTooLong
        FavouriteResult.NotSignedIn -> CompareMessage.Failed(DataFailure.NOT_SIGNED_IN)
        is FavouriteResult.Failed -> CompareMessage.Failed(failure)
        FavouriteResult.Saved, FavouriteResult.AlreadyFavourite -> CompareMessage.Failed(DataFailure.SERVER)
    }
}
