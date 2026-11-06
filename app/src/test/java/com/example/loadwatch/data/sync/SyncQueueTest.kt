package com.example.loadwatch.data.sync

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import com.example.loadwatch.core.network.dto.FavouriteChangeDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.SettingsChangeDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import com.example.loadwatch.fakes.FakePendingSyncDao
import com.example.loadwatch.fakes.FakeSyncScheduler
import com.example.loadwatch.fakes.testJson
import com.example.loadwatch.fakes.testSyncQueue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncQueueTest {

    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val dao = FakePendingSyncDao()
    private val scheduler = FakeSyncScheduler()
    private val queue = testSyncQueue(dao, scheduler, now)

    @Test
    fun `a queued report is stored under its client id and asks for a sync`() = runTest {
        queue.enqueueReport(report("client-1"))

        val item = dao.items.value.single()
        assertEquals("client-1", item.syncId)
        assertEquals(SyncEntityType.REPORT, item.entityType)
        assertEquals(PendingSyncStatus.PENDING, item.status)
        assertEquals(report("client-1"), testJson.decodeFromString<OutageReportCreateDto>(item.payloadJson))
        assertEquals(1, scheduler.requests.size)
    }

    @Test
    fun `queueing the same report twice keeps one row so it can only be sent once`() = runTest {
        queue.enqueueReport(report("client-1"))
        queue.enqueueReport(report("client-1"))

        assertEquals(1, dao.items.value.size)
    }

    @Test
    fun `different reports for the same area are all kept`() = runTest {
        queue.enqueueReport(report("client-1"))
        queue.enqueueReport(report("client-2"))

        assertEquals(setOf("client-1", "client-2"), dao.items.value.map { it.syncId }.toSet())
    }

    @Test
    fun `a newer favourite change replaces the older one for the same area`() = runTest {
        queue.enqueueFavourite("eth-berea", "Home", removed = false)
        queue.enqueueFavourite("eth-kloof", "Work", removed = false)
        queue.enqueueFavourite("eth-berea", label = null, removed = true)

        val berea = dao.items.value.filter { it.entityId == "eth-berea" }
        val change = testJson.decodeFromString<FavouriteChangeDto>(berea.single().payloadJson)
        assertTrue(change.removed)
        assertEquals(berea.single().syncId, change.changeId)
        assertEquals(2, dao.items.value.size)
    }

    @Test
    fun `only the latest settings change waits, with the time it was saved`() = runTest {
        val savedAt = Instant.parse("2026-11-06T09:30:00Z")
        queue.enqueueSettings("user-1", settings("zu"), savedAt.minusSeconds(60))
        queue.enqueueSettings("user-1", settings("af"), savedAt)

        val change = testJson.decodeFromString<SettingsChangeDto>(dao.items.value.single().payloadJson)
        assertEquals("af", change.languageCode)
        assertEquals(savedAt, change.changedAt)
    }

    @Test
    fun `rejected items are not counted as waiting`() = runTest {
        queue.enqueueReport(report("client-1"))
        queue.enqueueReport(report("client-2"))
        dao.update(dao.items.value.first().copy(status = PendingSyncStatus.REJECTED))

        assertEquals(1, queue.observeUnsentCount().first())
        assertTrue(queue.hasUnsent(SyncEntityType.REPORT))
        assertFalse(queue.hasUnsent(SyncEntityType.SETTINGS))
    }

    @Test
    fun `removing an item that reached the API another way empties its slot`() = runTest {
        queue.enqueueFavourite("eth-berea", "Home", removed = false)

        queue.remove(SyncEntityType.FAVOURITE, "eth-berea")

        assertTrue(dao.items.value.isEmpty())
    }

    private fun report(clientReportId: String) =
        OutageReportCreateDto("eth-berea", OutageEventType.POWER_OFF, now, now, clientReportId)

    private fun settings(language: String) =
        UserSettingsUpdateDto("eth-berea", 30, true, "POWER_OFF,STAGE_CHANGE", language, "dark", false)
}
