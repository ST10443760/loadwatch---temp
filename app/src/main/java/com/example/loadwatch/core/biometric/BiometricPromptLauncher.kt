package com.example.loadwatch.core.biometric

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.loadwatch.core.common.LogTags

// Shows the system's fingerprint or face prompt, with the PIN, pattern or password offered as the fallback.
// The prompt draws its own Cancel and "Use PIN" options, so no negative button is set.
fun FragmentActivity.showBiometricPrompt(
    title: String,
    subtitle: String?,
    onOutcome: (BiometricOutcome) -> Unit,
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Log.i(LogTags.BIOMETRIC, "Biometric prompt succeeded (type ${result.authenticationType})")
            onOutcome(BiometricOutcome.Succeeded)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val outcome = biometricOutcomeForError(errorCode, errString)
            Log.w(LogTags.BIOMETRIC, "Biometric prompt ended with error $errorCode: $outcome")
            onOutcome(outcome)
        }

        // A finger or face that did not match; the prompt stays open for another try
        override fun onAuthenticationFailed() {
            Log.d(LogTags.BIOMETRIC, "Biometric not recognised; the prompt stays open")
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { if (subtitle != null) setSubtitle(subtitle) }
        .setAllowedAuthenticators(APP_AUTHENTICATORS)
        .build()

    try {
        Log.i(LogTags.BIOMETRIC, "Showing the biometric prompt")
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(promptInfo)
    } catch (e: IllegalArgumentException) {
        // Thrown when the phone cannot offer this combination of authenticators
        Log.e(LogTags.BIOMETRIC, "Could not show the biometric prompt", e)
        onOutcome(BiometricOutcome.Unavailable)
    }
}

// The hosting activity, found through any wrapping contexts (the per-app language wraps the activity's context)
fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
