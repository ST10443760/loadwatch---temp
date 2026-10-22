package com.example.loadwatch.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.repository.SessionRepository
import com.example.loadwatch.data.repository.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : ViewModel() {

    // null until the session has been resolved
    val sessionStatus: StateFlow<SessionStatus?> = flow { emit(sessionRepository.resolveSession()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
