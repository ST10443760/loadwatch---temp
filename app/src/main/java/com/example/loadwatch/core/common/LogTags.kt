package com.example.loadwatch.core.common

// Logcat tags for the transitions NFR-08 requires to be observable.
// Filter Logcat on "LW." to see only LoadWatch output.
object LogTags {
    const val LIFECYCLE = "LW.Lifecycle"
    const val NAVIGATION = "LW.Navigation"
    const val AUTH = "LW.Auth"
    const val API = "LW.Api"
    const val ROOM = "LW.Room"
    const val SYNC = "LW.Sync"
    const val COUNTDOWN = "LW.Countdown"
}
