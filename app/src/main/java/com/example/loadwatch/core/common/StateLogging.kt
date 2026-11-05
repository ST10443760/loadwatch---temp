package com.example.loadwatch.core.common

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

// Logs a screen state each time its kind changes (Loading -> Ready, Ready -> Unavailable), not on every countdown
// tick, so Logcat follows the screen without being flooded (NFR-08). Meant for a flow collected once, e.g. before
// stateIn.
fun <T : Any> Flow<T>.logKindChanges(owner: String): Flow<T> {
    var lastKind: String? = null
    return onEach { state ->
        val kind = state::class.simpleName
        if (kind != lastKind) {
            Log.d(LogTags.STATE, "$owner: ${lastKind ?: "start"} -> $kind")
            lastKind = kind
        }
    }
}
