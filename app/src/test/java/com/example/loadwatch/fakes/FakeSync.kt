package com.example.loadwatch.fakes

import com.example.loadwatch.data.sync.SyncQueue
import com.example.loadwatch.data.sync.SyncScheduler
import java.time.Clock
import java.time.Instant

// Records sync requests instead of starting WorkManager
class FakeSyncScheduler : SyncScheduler {
    val requests = mutableListOf<String>()

    override fun requestSync(reason: String) {
        requests += reason
    }
}

fun testSyncQueue(
    dao: FakePendingSyncDao = FakePendingSyncDao(),
    scheduler: FakeSyncScheduler = FakeSyncScheduler(),
    now: Instant = Instant.parse("2026-11-06T10:00:00Z"),
) = SyncQueue(dao, scheduler, testJson, Clock.fixed(now, SAST))
