package com.example.loadwatch.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // The active area, read from Room so it shows offline and updates as soon as S05 switches it
    val activeArea: StateFlow<Area?> = settingsRepository.observeDefaultArea()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
