package com.example.loadwatch.feature.area

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.FavouriteResult
import com.example.loadwatch.data.repository.FavouritesRepository
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Outcome of a favourite change, shown inline on S05
sealed interface FavouriteMessage {
    data class Added(val areaName: String) : FavouriteMessage
    data object AlreadyFavourite : FavouriteMessage
    data class Removed(val areaName: String) : FavouriteMessage
    data object LabelTooLong : FavouriteMessage
    data class Failed(val failure: DataFailure) : FavouriteMessage
}

data class FavouriteEditState(
    // The area waiting for a label before it is saved as a favourite
    val pendingArea: Area? = null,
    val isSaving: Boolean = false,
    val message: FavouriteMessage? = null,
)

@HiltViewModel
class AreaSelectorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    areaRepository: AreaRepository,
    settingsRepository: SettingsRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {

    // Opened from S08 to save a favourite instead of switching the active area. Read from the route's
    // argument by name, so this feature does not depend on the navigation package.
    val addFavouriteMode: Boolean = savedStateHandle.get<Boolean>(ADD_FAVOURITE_ARG) ?: false

    val search = AreaSearch(areaRepository, viewModelScope)
    val choice = AreaChoice(viewModelScope, settingsRepository, areaRepository)

    val currentArea: StateFlow<Area?> = settingsRepository.observeDefaultArea()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentAreas: StateFlow<List<Area>> = areaRepository.observeRecentAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favourites: StateFlow<List<Favourite>> = favouritesRepository.observeFavourites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _favouriteEdit = MutableStateFlow(FavouriteEditState())
    val favouriteEdit: StateFlow<FavouriteEditState> = _favouriteEdit.asStateFlow()

    private val _leave = Channel<Unit>(Channel.BUFFERED)

    // Emits when the user should return: the switch was saved, the area was already current, or in add mode
    // the favourite was saved
    val finished: Flow<Unit> = merge(choice.done, _leave.receiveAsFlow())

    init {
        viewModelScope.launch {
            favouritesRepository.refresh()?.let { Log.w(LogTags.API, "Could not refresh favourites ($it); showing cached ones") }
        }
    }

    fun onAreaTapped(area: Area) {
        if (addFavouriteMode) {
            askForLabel(area)
        } else if (area.areaId == currentArea.value?.areaId) {
            _leave.trySend(Unit)
        } else {
            choice.save(area.areaId)
        }
    }

    fun isFavourite(area: Area): Boolean = favourites.value.any { it.area.areaId == area.areaId }

    fun onToggleFavourite(area: Area) {
        if (isFavourite(area)) remove(area) else askForLabel(area)
    }

    fun confirmFavourite(label: String) {
        val area = _favouriteEdit.value.pendingArea ?: return
        if (_favouriteEdit.value.isSaving) return
        _favouriteEdit.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = favouritesRepository.add(area.areaId, label)
            Log.i(LogTags.API, "Add favourite ${area.areaId}: ${result.javaClass.simpleName}")
            val message = result.toMessage(area)
            _favouriteEdit.value = FavouriteEditState(
                // Keep the dialog open only when the label itself needs fixing
                pendingArea = if (result == FavouriteResult.LabelTooLong) area else null,
                message = message,
            )
            if (addFavouriteMode && (result == FavouriteResult.Saved || result == FavouriteResult.AlreadyFavourite)) {
                _leave.send(Unit)
            }
        }
    }

    fun dismissLabelDialog() {
        _favouriteEdit.update { it.copy(pendingArea = null, isSaving = false) }
    }

    fun dismissMessage() {
        _favouriteEdit.update { it.copy(message = null) }
    }

    private fun askForLabel(area: Area) {
        _favouriteEdit.update { FavouriteEditState(pendingArea = area) }
    }

    private fun remove(area: Area) {
        viewModelScope.launch {
            val result = favouritesRepository.remove(area.areaId)
            Log.i(LogTags.API, "Remove favourite ${area.areaId}: ${result.javaClass.simpleName}")
            _favouriteEdit.update {
                it.copy(message = if (result == FavouriteResult.Saved) FavouriteMessage.Removed(area.name) else result.toMessage(area))
            }
        }
    }

    private fun FavouriteResult.toMessage(area: Area): FavouriteMessage = when (this) {
        FavouriteResult.Saved -> FavouriteMessage.Added(area.name)
        FavouriteResult.AlreadyFavourite -> FavouriteMessage.AlreadyFavourite
        FavouriteResult.LabelTooLong -> FavouriteMessage.LabelTooLong
        FavouriteResult.NotSignedIn -> FavouriteMessage.Failed(DataFailure.NOT_SIGNED_IN)
        is FavouriteResult.Failed -> FavouriteMessage.Failed(failure)
    }

    private companion object {
        // Matches AreaSelectorRoute.addFavourite
        const val ADD_FAVOURITE_ARG = "addFavourite"
    }
}
