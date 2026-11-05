package com.example.loadwatch.core.common

import android.util.Log
import androidx.lifecycle.ViewModel

// A ViewModel that logs when it is created and cleared, so Logcat shows each screen's state holder starting
// and ending next to the screen's own lifecycle events (NFR-08). A configuration change keeps the same instance,
// so a missing "created" line after a rotation shows the state survived.
abstract class LoggedViewModel : ViewModel() {

    private val name: String get() = javaClass.simpleName

    init {
        Log.d(LogTags.LIFECYCLE, "$name created")
    }

    override fun onCleared() {
        Log.d(LogTags.LIFECYCLE, "$name cleared")
        super.onCleared()
    }
}
