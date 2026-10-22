package com.example.loadwatch.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.model.AlertType
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.ThemePreference
import com.example.loadwatch.data.model.UserSettings
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.AuthRepository
import com.example.loadwatch.data.repository.FavouritesRepository
import com.example.loadwatch.data.repository.SaveSettingsResult
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// What the user is editing. The lead time is kept as text so out-of-range input can be shown and rejected.
data class SettingsForm(
    val defaultAreaId: String?,
    val leadMinutesText: String,
    val notificationsEnabled: Boolean,
    val alertTypes: Set<AlertType>,
    val languageCode: String,
    val themePreference: String,
    val biometricEnabled: Boolean,
) {
    companion object {
        fun from(settings: UserSettings) = SettingsForm(
            defaultAreaId = settings.defaultAreaId,
            leadMinutesText = settings.notificationLeadMinutes.toString(),
            notificationsEnabled = settings.notificationsEnabled,
            alertTypes = settings.enabledAlertTypes,
            languageCode = settings.languageCode,
            themePreference = settings.themePreference,
            biometricEnabled = settings.biometricEnabled,
        )
    }
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val email: String? = null,
    // Null when no settings exist yet, e.g. the API and the device both had nothing
    val saved: UserSettings? = null,
    val form: SettingsForm? = null,
    val areaChoices: List<Area> = emptyList(),
    // The API could not be reached on open, so the form shows what is saved on this phone
    val loadFailure: DataFailure? = null,
    val isSaving: Boolean = false,
    val saveResult: SaveSettingsResult? = null,
    val isSigningOut: Boolean = false,
) {
    val leadTime: LeadTimeInput? get() = form?.let { SettingsFormValidation.parseLeadMinutes(it.leadMinutesText) }
    val isDirty: Boolean get() = form != null && saved != null && form != SettingsForm.from(saved)
    val canSave: Boolean get() = isDirty && !isSaving && leadTime is LeadTimeInput.Valid
}

private data class EditState(
    // True once the first API refresh has finished, whether or not it succeeded
    val loadAttempted: Boolean = false,
    val form: SettingsForm? = null,
    val isSaving: Boolean = false,
    val saveResult: SaveSettingsResult? = null,
    val loadFailure: DataFailure? = null,
    val isSigningOut: Boolean = false,
)

// S09 Settings (FR-SET-01 to 05): opens from Room, then refreshes from the API; saves to Room and the API after
// validating every field.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    areaRepository: AreaRepository,
    favouritesRepository: FavouritesRepository,
) : ViewModel() {

    private val edit = MutableStateFlow(EditState())

    private val _signedOut = Channel<Unit>(Channel.BUFFERED)
    val signedOut: Flow<Unit> = _signedOut.receiveAsFlow()

    // The default area picker offers the current area, favourites and recent areas
    private val areaChoices: Flow<List<Area>> = combine(
        settingsRepository.observeDefaultArea(),
        favouritesRepository.observeFavourites(),
        areaRepository.observeRecentAreas(),
    ) { current, favourites, recents ->
        (listOfNotNull(current) + favourites.map { it.area } + recents).distinctBy { it.areaId }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.observeSettings(), areaChoices, edit) { saved, areas, edit ->
            SettingsUiState(
                isLoading = saved == null && !edit.loadAttempted,
                email = settingsRepository.currentUserEmail(),
                saved = saved,
                // Untouched forms follow the stored settings, so the API's copy replaces the cached one when it
                // arrives; once the user starts editing, their changes are kept
                form = edit.form ?: saved?.let(SettingsForm::from),
                areaChoices = areas,
                loadFailure = edit.loadFailure,
                isSaving = edit.isSaving,
                saveResult = edit.saveResult,
                isSigningOut = edit.isSigningOut,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        // Room first (observeSettings), then the API's copy
        viewModelScope.launch {
            val failure = settingsRepository.refreshFromServer()
            if (failure != null) Log.w(LogTags.API, "Could not load settings from the API ($failure); showing saved settings")
            edit.update { it.copy(loadAttempted = true, loadFailure = failure) }
        }
    }

    fun retryLoad() {
        viewModelScope.launch {
            val failure = settingsRepository.refreshFromServer()
            edit.update { it.copy(loadFailure = failure) }
        }
    }

    fun setDefaultArea(areaId: String) = updateForm { it.copy(defaultAreaId = areaId) }

    fun setLeadMinutesText(text: String) = updateForm { it.copy(leadMinutesText = text.filter(Char::isDigit).take(4)) }

    // The stepper moves in five-minute steps and never leaves the allowed range
    fun stepLeadMinutes(delta: Int) = updateForm { form ->
        val current = form.leadMinutesText.toIntOrNull() ?: SettingsRules.MIN_LEAD_MINUTES
        val stepped = (current + delta).coerceIn(SettingsRules.MIN_LEAD_MINUTES, SettingsRules.MAX_LEAD_MINUTES)
        form.copy(leadMinutesText = stepped.toString())
    }

    // Turning notifications off keeps the stored lead time (FR-SET-03)
    fun setNotificationsEnabled(enabled: Boolean) = updateForm { it.copy(notificationsEnabled = enabled) }

    fun setAlertType(type: AlertType, enabled: Boolean) =
        updateForm { it.copy(alertTypes = if (enabled) it.alertTypes + type else it.alertTypes - type) }

    // Stored and synced now; switching the app's strings is Final POE scope
    fun setLanguage(code: String) = updateForm { it.copy(languageCode = code) }

    fun setBiometricEnabled(enabled: Boolean) = updateForm { it.copy(biometricEnabled = enabled) }

    // The theme applies to the app straight away, so it is saved on its own rather than waiting for Save
    fun setTheme(theme: ThemePreference) {
        val saved = uiState.value.saved ?: return
        updateForm { it.copy(themePreference = theme.code) }
        viewModelScope.launch {
            val result = settingsRepository.save(saved.copy(themePreference = theme.code))
            Log.i(LogTags.API, "Theme saved as ${theme.code}: ${result.javaClass.simpleName}")
            edit.update { state ->
                // The rest of the form keeps any unsaved edits; the theme now matches what was stored
                state.copy(saveResult = result, form = state.form?.takeIf { it != SettingsForm.from(saved.copy(themePreference = theme.code)) })
            }
        }
    }

    fun save() {
        val state = uiState.value
        val saved = state.saved ?: return
        val form = state.form ?: return
        val leadMinutes = (state.leadTime as? LeadTimeInput.Valid)?.minutes ?: return
        if (state.isSaving) return

        val settings = saved.copy(
            defaultAreaId = form.defaultAreaId,
            notificationLeadMinutes = leadMinutes,
            notificationsEnabled = form.notificationsEnabled,
            enabledAlertTypes = form.alertTypes,
            languageCode = form.languageCode,
            themePreference = form.themePreference,
            biometricEnabled = form.biometricEnabled,
        )
        edit.update { it.copy(isSaving = true, saveResult = null) }
        viewModelScope.launch {
            val result = settingsRepository.save(settings)
            Log.i(LogTags.API, "Settings save finished: ${result.javaClass.simpleName}")
            edit.update {
                it.copy(
                    isSaving = false,
                    saveResult = result,
                    // A successful save makes the stored settings the form again
                    form = if (result is SaveSettingsResult.Saved || result is SaveSettingsResult.SavedOnDeviceOnly) null else it.form,
                )
            }
        }
    }

    fun retrySave() {
        if (uiState.value.saveResult is SaveSettingsResult.SavedOnDeviceOnly) {
            edit.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val result = settingsRepository.retrySync()
                edit.update { it.copy(isSaving = false, saveResult = result) }
            }
        } else {
            save()
        }
    }

    fun dismissSaveResult() = edit.update { it.copy(saveResult = null) }

    fun signOut() {
        if (edit.value.isSigningOut) return
        edit.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            authRepository.signOut()
            _signedOut.send(Unit)
        }
    }

    private fun updateForm(change: (SettingsForm) -> SettingsForm) {
        val current = uiState.value.form ?: return
        edit.update { it.copy(form = change(current), saveResult = null) }
    }
}
