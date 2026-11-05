package com.example.loadwatch.core.biometric

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import com.example.loadwatch.core.common.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Asks the system each time, because the user can add or remove a fingerprint or screen lock while the app runs
@Singleton
class AndroidBiometricChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricChecker {

    override fun availability(): BiometricAvailability {
        val result = BiometricManager.from(context).canAuthenticate(APP_AUTHENTICATORS)
        return biometricAvailabilityFor(result).also { Log.d(LogTags.BIOMETRIC, "Biometric availability: $it") }
    }
}
