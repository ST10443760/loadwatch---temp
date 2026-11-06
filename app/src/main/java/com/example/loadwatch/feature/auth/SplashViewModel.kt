package com.example.loadwatch.feature.auth

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.LoggedViewModel
import com.example.loadwatch.data.repository.SessionRepository
import com.example.loadwatch.data.repository.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the splash screen is still working out where to go, has decided, or could not tell. */
sealed interface SplashUiState {
    data object Resolving : SplashUiState
    data class Resolved(val status: SessionStatus) : SplashUiState
    data object Failed : SplashUiState
}

/**
 * View model for the splash screen. It restores the Firebase session and decides whether to open Login, Area
 * Onboarding or Home.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : LoggedViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Resolving)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        resolve()
    }

    fun retry() = resolve()

    private fun resolve() {
        _uiState.value = SplashUiState.Resolving
        viewModelScope.launch {
            _uiState.value = try {
                SplashUiState.Resolved(sessionRepository.resolveSession())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(LogTags.AUTH, "Could not resolve the session: ${e.javaClass.simpleName}", e)
                SplashUiState.Failed
            }
        }
    }
}
