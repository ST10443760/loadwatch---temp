package com.example.loadwatch.core.common

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Logs a screen's lifecycle events (ON_CREATE, ON_RESUME, ON_PAUSE, ...) against its navigation back stack entry,
// so Logcat shows when each destination becomes visible and when it is left (NFR-08).
@Composable
fun LogScreenLifecycle(screenName: String) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, screenName) {
        val observer = LifecycleEventObserver { _, event ->
            Log.d(LogTags.LIFECYCLE, "$screenName ${event.name}")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
