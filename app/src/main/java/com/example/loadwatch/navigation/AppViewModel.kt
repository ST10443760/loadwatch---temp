package com.example.loadwatch.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// App-wide session handling. When the API rejects the session even after a token refresh, sign out cleanly,
// clearing user-scoped data but keeping the area catalogue, and send the user back to S02.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

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
