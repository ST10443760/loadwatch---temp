package com.example.loadwatch.feature.area

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AreaSelectorViewModel @Inject constructor(
    areaRepository: AreaRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val search = AreaSearch(areaRepository, viewModelScope)
    val choice = AreaChoice(viewModelScope, settingsRepository, areaRepository)

    val currentArea: StateFlow<Area?> = settingsRepository.observeDefaultArea()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentAreas: StateFlow<List<Area>> = areaRepository.observeRecentAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _alreadyCurrent = Channel<Unit>(Channel.BUFFERED)

    // Emits when the user should return to Home: the switch was saved, or they picked the area already in use
    val finished: Flow<Unit> = merge(choice.done, _alreadyCurrent.receiveAsFlow())

    fun switchTo(area: Area) {
        if (area.areaId == currentArea.value?.areaId) {
            _alreadyCurrent.trySend(Unit)
        } else {
            choice.save(area.areaId)
        }
    }
}
