package com.example.loadwatch.fakes

import com.example.loadwatch.core.biometric.BiometricAvailability
import com.example.loadwatch.core.biometric.BiometricChecker

class FakeBiometricChecker(var availability: BiometricAvailability = BiometricAvailability.AVAILABLE) : BiometricChecker {
    override fun availability(): BiometricAvailability = availability
}
