package com.example.loadwatch.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.loadwatch.core.common.LogTags
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// One unique sync job with a network constraint: while the phone is offline it waits, and it runs as soon as
// connectivity returns. A newer request replaces the waiting or running job, which is safe because every queued
// item can be sent twice without harm.
// Reference: Android Developers, "Define work requests" (constraints and backoff) and "Managing work" (unique
// work) in the WorkManager guide, developer.android.com/develop/background-work/background-tasks/persistent
class WorkManagerSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncScheduler {

    override fun requestSync(reason: String) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        Log.i(LogTags.SYNC, "Sync requested ($reason); it runs once the phone is online")
    }

    private companion object {
        const val WORK_NAME = "loadwatch-sync"
        const val BACKOFF_SECONDS = 30L
    }
}
