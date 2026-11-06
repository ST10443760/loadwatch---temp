package com.example.loadwatch.core.biometric

import androidx.biometric.BiometricPrompt

// How a biometric prompt ended. Only Succeeded unlocks; every other outcome leaves the caller in control.
sealed interface BiometricOutcome {
    data object Succeeded : BiometricOutcome

    // The user closed the prompt, pressed Back, or the system dismissed it
    data object Cancelled : BiometricOutcome

    // Too many attempts; the sensor is locked for a while or until the phone is unlocked another way
    data object LockedOut : BiometricOutcome

    // The sensor, enrolment or screen lock went away between the availability check and the prompt
    data object Unavailable : BiometricOutcome

    // Anything else, with the system's own message, which is already in the phone's language
    data class Failed(val message: String) : BiometricOutcome
}

/**
 * Maps a BiometricPrompt error code to an outcome the lock screen can explain: cancelled, locked out,
 * unavailable or failed with the system's message.
 */
fun biometricOutcomeForError(errorCode: Int, message: CharSequence): BiometricOutcome = when (errorCode) {
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_CANCELED,
    -> BiometricOutcome.Cancelled

    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> BiometricOutcome.LockedOut

    BiometricPrompt.ERROR_NO_BIOMETRICS,
    BiometricPrompt.ERROR_HW_NOT_PRESENT,
    BiometricPrompt.ERROR_HW_UNAVAILABLE,
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
    -> BiometricOutcome.Unavailable

    else -> BiometricOutcome.Failed(message.toString())
}
