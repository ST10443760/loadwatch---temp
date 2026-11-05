package com.example.loadwatch.core.biometric

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Raised by a Google sign-in so the app shell can offer biometric unlock once. Kept in memory only, because
// the offer belongs to that sign-in rather than to every launch.
@Singleton
class BiometricOffer @Inject constructor() {

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun offerAfterSignIn() {
        Log.d(LogTags.BIOMETRIC, "Biometric unlock will be offered after this sign-in")
        _pending.value = true
    }

    fun dismiss() {
        _pending.value = false
    }
}
