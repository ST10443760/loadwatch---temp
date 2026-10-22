package com.example.loadwatch.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.authentication.GoogleSignInRequester
import com.example.loadwatch.core.authentication.GoogleSignInResult
import com.example.loadwatch.data.repository.AuthRepository
import com.example.loadwatch.data.repository.SessionRepository
import com.example.loadwatch.data.repository.SessionStatus
import com.example.loadwatch.data.repository.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginError {
    NO_GOOGLE_ACCOUNT,
    GOOGLE_SIGN_IN_FAILED,
    FIREBASE_SIGN_IN_FAILED,
}

data class LoginUiState(
    val isSigningIn: Boolean = false,
    val error: LoginError? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val googleSignInRequester: GoogleSignInRequester,
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _signedIn = Channel<SessionStatus>(Channel.BUFFERED)

    // Where to go once signed in: S03 if this account has no default area yet, otherwise S04
    val signedIn: Flow<SessionStatus> = _signedIn.receiveAsFlow()

    // activityContext hosts Credential Manager's account picker; it is only used for the duration of the call
    fun signIn(activityContext: Context) {
        if (_uiState.value.isSigningIn) return
        _uiState.value = LoginUiState(isSigningIn = true)

        viewModelScope.launch {
            val error = when (val google = googleSignInRequester.requestGoogleIdToken(activityContext)) {
                is GoogleSignInResult.Success -> signInToFirebase(google.googleIdToken)
                GoogleSignInResult.Cancelled -> null
                GoogleSignInResult.NoGoogleAccount -> LoginError.NO_GOOGLE_ACCOUNT
                GoogleSignInResult.Failed -> LoginError.GOOGLE_SIGN_IN_FAILED
            }
            _uiState.update { it.copy(isSigningIn = false, error = error) }
        }
    }

    private suspend fun signInToFirebase(googleIdToken: String): LoginError? {
        if (authRepository.signInWithGoogleIdToken(googleIdToken) == SignInResult.Failed) {
            return LoginError.FIREBASE_SIGN_IN_FAILED
        }
        val status = sessionRepository.resolveSession()
        if (status == SessionStatus.SignedOut) {
            return LoginError.FIREBASE_SIGN_IN_FAILED
        }
        _signedIn.send(status)
        return null
    }
}
