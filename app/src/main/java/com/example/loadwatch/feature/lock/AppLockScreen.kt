package com.example.loadwatch.feature.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.loadwatch.R
import com.example.loadwatch.core.biometric.BiometricAvailability
import com.example.loadwatch.core.biometric.BiometricOutcome
import com.example.loadwatch.core.biometric.findFragmentActivity
import com.example.loadwatch.core.biometric.showBiometricPrompt
import com.example.loadwatch.core.common.LogScreenLifecycle

// Covers the whole app on launch while biometric unlock is on. Asks for a fingerprint, face or the screen lock
// straight away; a cancelled or failed prompt leaves the user here with Unlock to try again and Sign out as the
// way out. If the phone no longer has any lock at all there is nothing to check against, so it lets the user in.
@Composable
fun AppLockScreen(
    availability: BiometricAvailability,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LogScreenLifecycle("Lock")
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)
    var outcome by remember { mutableStateOf<BiometricOutcome?>(null) }
    val hasScreenLock = availability == BiometricAvailability.AVAILABLE || availability == BiometricAvailability.UNAVAILABLE

    val prompt: () -> Unit = {
        if (activity == null) {
            outcome = BiometricOutcome.Unavailable
        } else {
            activity.showBiometricPrompt(promptTitle, promptSubtitle) { result ->
                if (result == BiometricOutcome.Succeeded) onUnlocked() else outcome = result
            }
        }
    }

    LaunchedEffect(Unit) {
        if (availability == BiometricAvailability.AVAILABLE) prompt()
    }

    // Back leaves the app rather than reaching the screens behind the lock
    BackHandler { activity?.finish() }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = lockMessage(hasScreenLock, outcome),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (hasScreenLock) {
                Button(onClick = prompt) { Text(stringResource(R.string.lock_unlock)) }
            } else {
                Button(onClick = onUnlocked) { Text(stringResource(R.string.lock_continue)) }
            }
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.lock_sign_out)) }
        }
    }
}

/** The lock screen message for the last biometric outcome, such as cancelled or too many attempts. */
@Composable
private fun lockMessage(hasScreenLock: Boolean, outcome: BiometricOutcome?): String = when {
    !hasScreenLock -> stringResource(R.string.lock_no_screen_lock)
    outcome == BiometricOutcome.Cancelled -> stringResource(R.string.lock_cancelled)
    outcome == BiometricOutcome.LockedOut -> stringResource(R.string.lock_locked_out)
    outcome == BiometricOutcome.Unavailable -> stringResource(R.string.lock_prompt_unavailable)
    outcome is BiometricOutcome.Failed -> outcome.message
    else -> stringResource(R.string.lock_message)
}
