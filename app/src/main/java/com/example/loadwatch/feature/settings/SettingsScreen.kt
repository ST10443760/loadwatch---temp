package com.example.loadwatch.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.model.AlertType
import com.example.loadwatch.data.model.AppLanguage
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.ThemePreference
import com.example.loadwatch.data.repository.SaveSettingsResult

// S09 Settings: default area, alert lead time, notifications, alert types, language, theme and biometric unlock
// (FR-SET-01 to 05), plus the signed-in account and sign-out (FR-AUTH-04).
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    onFindArea: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S09")
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.signedOut.collect { onSignedOut() }
    }

    val form = state.form
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        form == null -> Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            RecoveryState(
                title = stringResource(R.string.settings_load_failed_title),
                message = stringResource(R.string.settings_load_failed_message),
                onRetry = viewModel::retryLoad,
                secondaryActionLabel = stringResource(R.string.settings_sign_out),
                onSecondaryAction = viewModel::signOut,
            )
        }
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            if (state.loadFailure != null) {
                InlineMessage(
                    message = stringResource(R.string.settings_cached_notice),
                    isError = false,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::retryLoad,
                )
            }

            Section(title = stringResource(R.string.settings_default_area)) {
                DefaultAreaPicker(
                    selectedId = form.defaultAreaId,
                    choices = state.areaChoices,
                    onSelect = viewModel::setDefaultArea,
                    onFindArea = onFindArea,
                )
            }

            Section(title = stringResource(R.string.settings_notifications)) {
                SwitchRow(
                    label = stringResource(R.string.settings_notifications_enabled),
                    checked = form.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
                if (!form.notificationsEnabled) {
                    Note(stringResource(R.string.settings_notifications_off_note))
                }
                LeadTimeStepper(
                    text = form.leadMinutesText,
                    input = state.leadTime,
                    onTextChange = viewModel::setLeadMinutesText,
                    onStep = viewModel::stepLeadMinutes,
                )
                Text(stringResource(R.string.settings_alert_types), style = MaterialTheme.typography.labelLarge)
                AlertType.entries.forEach { type ->
                    CheckboxRow(
                        label = stringResource(type.labelRes()),
                        checked = type in form.alertTypes,
                        onCheckedChange = { viewModel.setAlertType(type, it) },
                    )
                }
            }

            Section(title = stringResource(R.string.settings_language)) {
                AppLanguage.entries.forEach { language ->
                    RadioRow(
                        label = stringResource(language.labelRes()),
                        selected = form.languageCode == language.code,
                        onClick = { viewModel.setLanguage(language.code) },
                    )
                }
                Note(stringResource(R.string.settings_language_note))
            }

            Section(title = stringResource(R.string.settings_theme)) {
                ThemePreference.entries.forEach { theme ->
                    RadioRow(
                        label = stringResource(theme.labelRes()),
                        selected = form.themePreference == theme.code,
                        onClick = { viewModel.setTheme(theme) },
                    )
                }
                Note(stringResource(R.string.settings_theme_note))
            }

            Section(title = stringResource(R.string.settings_security)) {
                SwitchRow(
                    label = stringResource(R.string.settings_biometric),
                    checked = form.biometricEnabled,
                    onCheckedChange = viewModel::setBiometricEnabled,
                )
                Note(stringResource(R.string.settings_biometric_note))
            }

            SaveArea(state = state, onSave = viewModel::save, onRetry = viewModel::retrySave, onDismiss = viewModel::dismissSaveResult)

            HorizontalDivider()
            state.email?.let { email ->
                Text(
                    text = stringResource(R.string.settings_signed_in_as, email),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = viewModel::signOut, enabled = !state.isSigningOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.isSigningOut) R.string.settings_signing_out else R.string.settings_sign_out))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DefaultAreaPicker(selectedId: String?, choices: List<Area>, onSelect: (String) -> Unit, onFindArea: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.areaId == selectedId }
    Column {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name ?: stringResource(R.string.settings_default_area_none))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { area ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_area_choice, area.name, area.municipality)) },
                        onClick = {
                            expanded = false
                            onSelect(area.areaId)
                        },
                    )
                }
            }
        }
        TextButton(onClick = onFindArea) { Text(stringResource(R.string.settings_find_area)) }
    }
}

// Five-minute steps that stay inside 5 to 120; typed values outside the range are rejected inline (FR-SET-02)
@Composable
private fun LeadTimeStepper(text: String, input: LeadTimeInput?, onTextChange: (String) -> Unit, onStep: (Int) -> Unit) {
    val minutes = text.toIntOrNull()
    val decreaseDescription = stringResource(R.string.settings_lead_decrease)
    val increaseDescription = stringResource(R.string.settings_lead_increase)
    Text(stringResource(R.string.settings_lead_time), style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onStep(-LEAD_STEP) },
            enabled = minutes == null || minutes > SettingsRules.MIN_LEAD_MINUTES,
            modifier = Modifier.semantics { contentDescription = decreaseDescription },
        ) { Text("−") }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.width(120.dp),
            singleLine = true,
            suffix = { Text(stringResource(R.string.settings_minutes_suffix)) },
            isError = input !is LeadTimeInput.Valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(
            onClick = { onStep(LEAD_STEP) },
            enabled = minutes == null || minutes < SettingsRules.MAX_LEAD_MINUTES,
            modifier = Modifier.semantics { contentDescription = increaseDescription },
        ) { Text("+") }
    }
    val error = when (input) {
        LeadTimeInput.Empty -> stringResource(R.string.settings_lead_empty)
        LeadTimeInput.NotWholeNumber, LeadTimeInput.OutOfRange ->
            stringResource(R.string.settings_lead_out_of_range, SettingsRules.MIN_LEAD_MINUTES, SettingsRules.MAX_LEAD_MINUTES)
        is LeadTimeInput.Valid, null -> null
    }
    Text(
        text = error ?: stringResource(R.string.settings_lead_hint, SettingsRules.MIN_LEAD_MINUTES, SettingsRules.MAX_LEAD_MINUTES),
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SaveArea(state: SettingsUiState, onSave: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val result = state.saveResult) {
            null -> Unit
            SaveSettingsResult.Saved -> InlineMessage(
                message = stringResource(R.string.settings_saved),
                isError = false,
                actionLabel = stringResource(R.string.action_close),
                onAction = onDismiss,
            )
            is SaveSettingsResult.SavedOnDeviceOnly -> InlineMessage(
                message = stringResource(R.string.settings_saved_on_device),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            is SaveSettingsResult.Rejected -> InlineMessage(
                message = stringResource(R.string.settings_rejected, result.fields.joinToString(", ")),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            is SaveSettingsResult.Failed -> InlineMessage(
                message = stringResource(
                    when (result.failure) {
                        DataFailure.OFFLINE -> R.string.settings_failed_offline
                        DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_message
                        DataFailure.SERVER, DataFailure.NOT_FOUND -> R.string.settings_failed
                    },
                ),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
        }
        Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.isSaving) R.string.settings_saving else R.string.settings_save))
        }
    }
}

private fun AlertType.labelRes(): Int = when (this) {
    AlertType.POWER_OFF -> R.string.settings_alert_power_off
    AlertType.POWER_BACK_ON -> R.string.settings_alert_power_back
    AlertType.STAGE_CHANGE -> R.string.settings_alert_stage_change
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.ISIZULU -> R.string.settings_language_isizulu
    AppLanguage.AFRIKAANS -> R.string.settings_language_afrikaans
}

private fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.settings_theme_system
    ThemePreference.LIGHT -> R.string.settings_theme_light
    ThemePreference.DARK -> R.string.settings_theme_dark
}

private const val LEAD_STEP = 5
