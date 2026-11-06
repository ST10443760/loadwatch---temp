package com.example.loadwatch.data.sync

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// On launch, asks for a sync if anything is still queued from an earlier session, such as a report saved just
// before the app was closed
@Singleton
class SyncStarter @Inject constructor(
    private val queue: SyncQueue,
    private val scheduler: SyncScheduler,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            val unsent = queue.observeUnsentCount().first()
            if (unsent > 0) {
                Log.i(LogTags.SYNC, "$unsent changes still waiting from before")
                scheduler.requestSync("app start")
            }
        }
    }
}
