package com.example.loadwatch.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.model.ThemePreference
import com.example.loadwatch.data.repository.AuthRepository
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// App-wide session and theme handling. When the API rejects the session even after a token refresh, sign out cleanly,
// clearing user-scoped data but keeping the area catalogue, and send the user back to S02.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    // The S09 theme choice for the whole app; null until settings exist, when the dark-first default applies
    val themePreference: StateFlow<ThemePreference?> = settingsRepository.observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessionEnded = Channel<Unit>(Channel.CONFLATED)
    val sessionEnded: Flow<Unit> = _sessionEnded.receiveAsFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionExpired.collect {
                authRepository.signOut()
                _sessionEnded.send(Unit)
            }
        }
    }
}
