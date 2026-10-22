package com.example.loadwatch.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderAction
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S09 Settings: area, lead time, alert types, language, theme and biometrics (FR-SET-01 to 05).
// The settings form is still a placeholder; sign-out is live and returns to S02 (FR-AUTH-04).
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val isSigningOut by viewModel.isSigningOut.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.signedOut.collect { onSignedOut() }
    }

    PlaceholderScreen(
        screenId = stringResource(R.string.settings_screen_id),
        title = stringResource(R.string.settings_title),
        purpose = stringResource(R.string.settings_purpose),
        modifier = modifier,
        actions = listOf(
            PlaceholderAction(
                label = stringResource(if (isSigningOut) R.string.settings_signing_out else R.string.settings_sign_out),
                onClick = viewModel::signOut,
            ),
        ),
    )
}
