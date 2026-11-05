package com.example.loadwatch.navigation

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.biometric.BiometricAvailability
import com.example.loadwatch.core.biometric.BiometricChecker
import com.example.loadwatch.core.biometric.BiometricOffer
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.LoggedViewModel
import com.example.loadwatch.core.notifications.PushPreferences
import com.example.loadwatch.data.model.AppLanguage
import com.example.loadwatch.data.model.ThemePreference
import com.example.loadwatch.data.repository.AuthRepository
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppLockState {
    // Reading the saved settings to decide whether to ask for biometrics
    CHECKING,
    LOCKED,
    UNLOCKED,
}

// App-wide session, theme and biometric lock handling. When the API rejects the session even after a token refresh,
// sign out cleanly, clearing user-scoped data but keeping the area catalogue, and send the user back to S02.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
    private val biometricChecker: BiometricChecker,
    private val biometricOffer: BiometricOffer,
    private val pushPreferences: PushPreferences,
) : LoggedViewModel() {

    // The S09 theme choice for the whole app; null until settings exist, when the dark-first default applies
    val themePreference: StateFlow<ThemePreference?> = settingsRepository.observeThemePreference()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // The S09 language for the whole app; null until settings exist, when the phone's language is used
    val language: StateFlow<AppLanguage?> = settingsRepository.observeLanguage()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _sessionEnded = Channel<Unit>(Channel.CONFLATED)
    val sessionEnded: Flow<Unit> = _sessionEnded.receiveAsFlow()

    // This ViewModel outlives configuration changes, including the recreation a language change causes, so the
    // lock is only shown when the app is launched, not after every rotation
    private val _lockState = MutableStateFlow(AppLockState.CHECKING)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private val _signedOutFromLock = Channel<Unit>(Channel.CONFLATED)
    val signedOutFromLock: Flow<Unit> = _signedOutFromLock.receiveAsFlow()

    // Offered once after a Google sign-in, when settings exist, unlock is off and this phone can use it
    val showBiometricOffer: StateFlow<Boolean> =
        combine(biometricOffer.pending, settingsRepository.observeSettings()) { pending, settings ->
            pending && settings != null && !settings.biometricEnabled &&
                biometricChecker.availability() == BiometricAvailability.AVAILABLE
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val notificationPromptShown = MutableStateFlow(pushPreferences.permissionPromptShown)

    // Alerts are on in Settings and the explanation before Android's permission dialog has not been shown on this
    // phone yet. The screen adds whether Android still needs to ask, which only it can check.
    val wantsNotificationPermission: StateFlow<Boolean> =
        combine(settingsRepository.observeSettings(), notificationPromptShown) { settings, shown ->
            settings?.notificationsEnabled == true && !shown
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            authRepository.sessionExpired.collect {
                Log.i(LogTags.AUTH, "Session expired; signing out and returning to sign-in")
                authRepository.signOut()
                _sessionEnded.send(Unit)
            }
        }
        viewModelScope.launch {
            // Decided from the settings saved on this phone, so the lock also works offline
            val biometricEnabled = settingsRepository.observeSettings().first()?.biometricEnabled == true
            _lockState.value = if (biometricEnabled) AppLockState.LOCKED else AppLockState.UNLOCKED
            Log.i(LogTags.BIOMETRIC, "App launch lock: ${_lockState.value}")
        }
    }

    fun biometricAvailability(): BiometricAvailability = biometricChecker.availability()

    fun onUnlocked() {
        Log.i(LogTags.BIOMETRIC, "App unlocked")
        _lockState.value = AppLockState.UNLOCKED
    }

    // The way out when biometrics keep failing: sign out and sign in with Google again
    fun signOutFromLock() {
        viewModelScope.launch {
            Log.i(LogTags.BIOMETRIC, "Signing out from the lock screen")
            authRepository.signOut()
            _lockState.value = AppLockState.UNLOCKED
            _signedOutFromLock.send(Unit)
        }
    }

    fun dismissBiometricOffer() {
        Log.d(LogTags.BIOMETRIC, "Biometric unlock offer dismissed")
        biometricOffer.dismiss()
    }

    // Shown once per phone, whatever the answer; after that Settings explains how to allow notifications
    fun onNotificationPromptShown() {
        pushPreferences.permissionPromptShown = true
        notificationPromptShown.value = true
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            Log.i(LogTags.PUSH, "Notification permission granted")
        } else {
            Log.w(LogTags.PUSH, "Notification permission denied; stage alerts will not be shown")
        }
    }
}
