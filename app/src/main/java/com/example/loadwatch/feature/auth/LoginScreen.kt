package com.example.loadwatch.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderAction
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S02 SSO Login: Google sign-in and why an account is needed (FR-AUTH-01).
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        screenId = stringResource(R.string.login_screen_id),
        title = stringResource(R.string.login_title),
        purpose = stringResource(R.string.login_purpose),
        modifier = modifier,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.login_continue_with_google), onSignedIn, isPrimary = true),
        ),
    )
}
