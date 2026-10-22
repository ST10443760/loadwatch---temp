package com.example.loadwatch.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderScreen
import com.example.loadwatch.data.repository.SessionStatus

// S01 Splash: resolves session and cached settings, then routes without showing the user a decision.
// S02 if signed out, S03 if there is no default area, S04 otherwise.
@Composable
fun SplashScreen(
    onSignedOut: () -> Unit,
    onNeedsArea: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val sessionStatus by viewModel.sessionStatus.collectAsStateWithLifecycle()

    LaunchedEffect(sessionStatus) {
        when (sessionStatus) {
            SessionStatus.SignedOut -> onSignedOut()
            SessionStatus.NeedsArea -> onNeedsArea()
            SessionStatus.Ready -> onReady()
            null -> Unit
        }
    }

    PlaceholderScreen(
        screenId = stringResource(R.string.splash_screen_id),
        title = stringResource(R.string.splash_title),
        purpose = stringResource(R.string.splash_purpose),
        modifier = modifier,
    )
}
