package com.example.loadwatch.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.loadwatch.core.common.LogTags
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

// Runs SyncEngine in the background. WorkManager only starts it while the phone is online, and runs it again with
// a growing delay when it asks to retry.
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun syncEngine(): SyncEngine
    }

    override suspend fun doWork(): Result {
        Log.i(LogTags.SYNC, "Sync worker started (attempt ${runAttemptCount + 1})")
        val engine = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java).syncEngine()
        return try {
            when (engine.syncNow()) {
                SyncRunResult.DONE -> Result.success().also { Log.i(LogTags.SYNC, "Sync worker finished") }
                SyncRunResult.RETRY -> Result.retry().also { Log.i(LogTags.SYNC, "Sync worker will retry with backoff") }
            }
        } catch (e: CancellationException) {
            // Replaced by a newer sync request or stopped by the system; everything it sent is safe to send again
            Log.i(LogTags.SYNC, "Sync worker stopped before finishing")
            throw e
        } catch (e: Exception) {
            Log.e(LogTags.SYNC, "Sync worker failed unexpectedly; will retry", e)
            Result.retry()
        }
    }
}
