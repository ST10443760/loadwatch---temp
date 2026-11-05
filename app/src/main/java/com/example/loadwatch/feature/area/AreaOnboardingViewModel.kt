package com.example.loadwatch.feature.area

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.LoggedViewModel
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
) : LoggedViewModel() {

    val search = AreaSearch(areaRepository, viewModelScope)
    val choice = AreaChoice(viewModelScope, settingsRepository, areaRepository)

    // Kept separately from the results so the choice survives editing the search
    private val _selectedArea = MutableStateFlow<Area?>(null)
    val selectedArea: StateFlow<Area?> = _selectedArea.asStateFlow()

    fun select(area: Area) {
        _selectedArea.value = area
        choice.clearProblem()
        Log.d(LogTags.NAVIGATION, "Onboarding area selected: ${area.areaId}")
    }

    fun confirm() {
        _selectedArea.value?.let {
            Log.i(LogTags.NAVIGATION, "Onboarding area confirmed: ${it.areaId}")
            choice.save(it.areaId)
        }
    }
}
