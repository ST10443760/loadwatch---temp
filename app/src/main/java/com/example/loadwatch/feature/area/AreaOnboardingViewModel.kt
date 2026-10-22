package com.example.loadwatch.feature.area

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AreaOnboardingViewModel @Inject constructor(
    areaRepository: AreaRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val search = AreaSearch(areaRepository, viewModelScope)
    val choice = AreaChoice(viewModelScope, settingsRepository, areaRepository)

    // Kept separately from the results so the choice survives editing the search
    private val _selectedArea = MutableStateFlow<Area?>(null)
    val selectedArea: StateFlow<Area?> = _selectedArea.asStateFlow()

    fun select(area: Area) {
        _selectedArea.value = area
        choice.clearProblem()
    }

    fun confirm() {
        _selectedArea.value?.let { choice.save(it.areaId) }
    }
}
