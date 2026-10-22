package com.example.loadwatch.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.data.repository.SessionStatus

// S01 Splash: resolves the Firebase session and cached settings, then routes without showing the user a decision:
// S02 if signed out, S03 if there is no default area, S04 otherwise (FR-AUTH-02).
@Composable
fun SplashScreen(
    onSignedOut: () -> Unit,
    onNeedsArea: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S01")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is SplashUiState.Resolved) {
            when (state.status) {
                SessionStatus.SignedOut -> onSignedOut()
                SessionStatus.NeedsArea -> onNeedsArea()
                SessionStatus.Ready -> onReady()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (uiState is SplashUiState.Failed) {
            RecoveryState(
                title = stringResource(R.string.splash_error_title),
                message = stringResource(R.string.splash_error_message),
                onRetry = viewModel::retry,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.splash_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.splash_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
