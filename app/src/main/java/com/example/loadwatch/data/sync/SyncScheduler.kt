package com.example.loadwatch.data.sync

// Starts a background sync of the pending_sync queue. An interface so repositories can be unit tested without
// WorkManager.
interface SyncScheduler {
    // reason is only logged, to show in Logcat what asked for the sync
    fun requestSync(reason: String)
}
