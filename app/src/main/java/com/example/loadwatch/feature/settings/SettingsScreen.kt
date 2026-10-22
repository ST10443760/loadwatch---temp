package com.example.loadwatch.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderAction
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S09 Settings: area, lead time, alert types, language, theme and biometrics (FR-SET-01 to 05).
// Signing out returns to S02 (FR-AUTH-04).
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        screenId = stringResource(R.string.settings_screen_id),
        title = stringResource(R.string.settings_title),
        purpose = stringResource(R.string.settings_purpose),
        modifier = modifier,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.settings_sign_out), onSignedOut),
        ),
    )
}
