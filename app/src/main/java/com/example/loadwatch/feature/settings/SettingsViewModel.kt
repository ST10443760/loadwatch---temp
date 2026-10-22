package com.example.loadwatch.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    private val _signedOut = Channel<Unit>(Channel.BUFFERED)
    val signedOut: Flow<Unit> = _signedOut.receiveAsFlow()

    // Clears the Firebase session and user-scoped data, keeping the public area catalogue (FR-AUTH-04)
    fun signOut() {
        if (_isSigningOut.value) return
        _isSigningOut.value = true
        viewModelScope.launch {
            authRepository.signOut()
            _signedOut.send(Unit)
        }
    }
}
