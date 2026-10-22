package com.example.loadwatch.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.repository.SessionRepository
import com.example.loadwatch.data.repository.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashUiState {
    data object Resolving : SplashUiState
    data class Resolved(val status: SessionStatus) : SplashUiState
    data object Failed : SplashUiState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

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
                Log.e(LogTags.AUTH, "Could not resolve the session: ${e.javaClass.simpleName}")
                SplashUiState.Failed
            }
        }
    }
}
