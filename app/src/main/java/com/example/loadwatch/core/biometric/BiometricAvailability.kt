package com.example.loadwatch.core.biometric

import androidx.biometric.BiometricManager

// Whether this phone can confirm it is the owner, by fingerprint, face or the screen lock as a fallback
enum class BiometricAvailability {
    AVAILABLE,

    // There is a sensor, but no fingerprint, face or screen lock is set up yet
    NONE_ENROLLED,

    // No fingerprint or face sensor and no screen lock to fall back to
    NO_HARDWARE,

    // The sensor is busy, needs a security update or reports something unknown
    UNAVAILABLE,
}

// Fingerprint or face (any class), with the PIN, pattern or password as the fallback. BIOMETRIC_WEAK is used
// because BIOMETRIC_STRONG cannot be combined with DEVICE_CREDENTIAL before Android 11.
const val APP_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun biometricAvailabilityFor(canAuthenticateResult: Int): BiometricAvailability = when (canAuthenticateResult) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
    else -> BiometricAvailability.UNAVAILABLE
}

interface BiometricChecker {
    fun availability(): BiometricAvailability
}
