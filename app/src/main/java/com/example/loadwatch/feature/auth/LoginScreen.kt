package com.example.loadwatch.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.data.repository.SessionStatus

// S02 SSO Login: Google sign-in through Credential Manager and why an account is needed (FR-AUTH-01, 03).
@Composable
fun LoginScreen(
    sessionExpired: Boolean,
    onNeedsArea: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S02")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.signedIn.collect { status ->
            if (status == SessionStatus.Ready) onReady() else onNeedsArea()
        }
    }

    LoginContent(
        uiState = uiState,
        sessionExpired = sessionExpired,
        onSignIn = { viewModel.signIn(context) },
        modifier = modifier,
    )
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    sessionExpired: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        if (sessionExpired) {
            InlineMessage(message = stringResource(R.string.login_session_expired), isError = false)
        }

        Text(
            text = stringResource(R.string.login_why_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.login_purpose),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onSignIn,
            enabled = !uiState.isSigningIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSigningIn) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.login_signing_in))
                }
            } else {
                Text(stringResource(R.string.login_continue_with_google))
            }
        }

        // Inline error slot: a labelled reason with a way to try again
        uiState.error?.let { error ->
            InlineMessage(
                message = stringResource(error.messageRes()),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onSignIn,
            )
        }

        TextButton(onClick = { showPrivacy = true }) {
            Text(stringResource(R.string.login_privacy_link))
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(stringResource(R.string.login_privacy_dialog_title)) },
            text = { Text(stringResource(R.string.login_privacy_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

private fun LoginError.messageRes(): Int = when (this) {
    LoginError.NO_GOOGLE_ACCOUNT -> R.string.login_error_no_google_account
    LoginError.GOOGLE_SIGN_IN_FAILED -> R.string.login_error_google_failed
    LoginError.FIREBASE_SIGN_IN_FAILED -> R.string.login_error_firebase_failed
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
private fun LoginContentPreview() {
    LoadWatchTheme {
        Surface {
            LoginContent(
                uiState = LoginUiState(error = LoginError.GOOGLE_SIGN_IN_FAILED),
                sessionExpired = true,
                onSignIn = {},
            )
        }
    }
}
