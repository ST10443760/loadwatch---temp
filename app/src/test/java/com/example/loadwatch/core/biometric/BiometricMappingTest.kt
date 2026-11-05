package com.example.loadwatch.core.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

// The lock screen and Settings must never crash or unlock by mistake when the prompt ends without success
class BiometricMappingTest {

    @Test
    fun `a phone with a fingerprint, face or screen lock is available`() {
        assertEquals(BiometricAvailability.AVAILABLE, biometricAvailabilityFor(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun `missing enrolment and missing hardware are told apart`() {
        assertEquals(BiometricAvailability.NONE_ENROLLED, biometricAvailabilityFor(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertEquals(BiometricAvailability.NO_HARDWARE, biometricAvailabilityFor(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
    }

    @Test
    fun `a busy sensor, a pending security update or an unknown answer is unavailable`() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        ).forEach { assertEquals(BiometricAvailability.UNAVAILABLE, biometricAvailabilityFor(it)) }
    }

    @Test
    fun `closing the prompt or pressing back is a cancel`() {
        listOf(BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED)
            .forEach { assertEquals(BiometricOutcome.Cancelled, biometricOutcomeForError(it, "")) }
    }

    @Test
    fun `too many attempts is a lockout`() {
        listOf(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
            .forEach { assertEquals(BiometricOutcome.LockedOut, biometricOutcomeForError(it, "")) }
    }

    @Test
    fun `a sensor or screen lock that disappeared is unavailable`() {
        listOf(
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        ).forEach { assertEquals(BiometricOutcome.Unavailable, biometricOutcomeForError(it, "")) }
    }

    @Test
    fun `any other error keeps the system's message`() {
        assertEquals(
            BiometricOutcome.Failed("Sensor timed out"),
            biometricOutcomeForError(BiometricPrompt.ERROR_TIMEOUT, "Sensor timed out"),
        )
    }
}
