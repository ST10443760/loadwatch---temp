package com.example.loadwatch.data.sync

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.SettingsChangeDto
import com.example.loadwatch.core.network.dto.SyncRejectionDto
import com.example.loadwatch.core.network.dto.SyncResultDto
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.remote.ReportRemoteDataSource
import com.example.loadwatch.data.remote.ScheduleRemoteDataSource
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.remote.SyncRemoteDataSource
import com.example.loadwatch.data.repository.OfflineFirstReportRepository
import com.example.loadwatch.data.repository.SettingsRepository
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAreaStatusDao
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeFavouriteAreaDao
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakeOutageReportDao
import com.example.loadwatch.fakes.FakePendingSyncDao
import com.example.loadwatch.fakes.FakeSyncScheduler
import com.example.loadwatch.fakes.FakeUserSettingsDao
import com.example.loadwatch.fakes.SAST
import com.example.loadwatch.fakes.httpError
import com.example.loadwatch.fakes.offline
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant

class SyncEngineTest {

    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val clock = Clock.fixed(now, SAST)
    private val api = FakeLoadWatchApi()
    private val authSession = FakeAuthSession(USER)
    private val pendingDao = FakePendingSyncDao()
    private val queue = SyncQueue(pendingDao, FakeSyncScheduler(), testJson, clock)
    private val reportDao = FakeOutageReportDao()
    private val areaDao = FakeAreaDao(listOf(area(BEREA), area(KLOOF)))
    private val favouriteDao = FakeFavouriteAreaDao(areaDao)
    private val settingsDao = FakeUserSettingsDao()
    private val areaRepository = FakeAreaRepository(areaDao)
    private val settingsRepository = SettingsRepository(
        authSession = authSession,
        local = SettingsLocalDataSource(settingsDao),
        remote = SettingsRemoteDataSource(api, testJson),
        areaRepository = areaRepository,
        syncQueue = queue,
    )
    private val engine = SyncEngine(
        authSession = authSession,
        pendingSyncDao = pendingDao,
        remote = SyncRemoteDataSource(api, testJson),
        outageReportDao = reportDao,
        favouriteAreaDao = favouriteDao,
        areaRepository = areaRepository,
        reportRepository = OfflineFirstReportRepository(
            authSession,
            ReportRemoteDataSource(api, testJson),
            ScheduleRemoteDataSource(api, testJson),
            reportDao,
            FakeAreaStatusDao(),
            queue,
            clock,
        ),
        settingsRepository = settingsRepository,
        json = testJson,
        clock = clock,
    )

    @Test
    fun `nothing is sent when nobody is signed in`() = runTest {
        queueReport("client-1")
        authSession.signedInUserId.value = null

        assertEquals(SyncRunResult.DONE, engine.syncNow())
        assertEquals(0, api.callCount("sync"))
    }

    @Test
    fun `an empty queue makes no request`() = runTest {
        assertEquals(SyncRunResult.DONE, engine.syncNow())
        assertEquals(0, api.callCount("sync"))
    }

    @Test
    fun `reports, favourite changes and settings go together in one request`() = runTest {
        queueReport("client-1")
        queue.enqueueFavourite(KLOOF, "Work", removed = false)
        queue.enqueueSettings(USER, settingsUpdate("af"), now)
        api.syncHandler = { batch ->
            val ids = batch.reports.map { it.clientReportId } + batch.favourites.map { it.changeId } + listOfNotNull(batch.settings?.changeId)
            SyncResultDto(accepted = ids, syncedAt = now)
        }

        engine.syncNow()

        val batch = api.syncBatches.single()
        assertEquals(listOf("client-1"), batch.reports.map { it.clientReportId })
        assertEquals(listOf(KLOOF), batch.favourites.map { it.areaId })
        assertEquals("af", batch.settings?.languageCode)
    }


    @Test
    fun `an item the API does not answer is sent once per run and waits for the next`() = runTest {
        queueReport("client-1")
        api.syncHandler = { SyncResultDto(syncedAt = now) }

        assertEquals(SyncRunResult.RETRY, engine.syncNow())

        assertEquals(1, api.callCount("sync"))
        assertEquals(PendingSyncStatus.RETRYING, pendingDao.items.value.single().status)
    }
    @Test
    fun `an accepted report leaves the queue and is marked synced`() = runTest {
        queueReport("client-1")
        api.syncHandler = { SyncResultDto(accepted = listOf("client-1"), syncedAt = now) }

        assertEquals(SyncRunResult.DONE, engine.syncNow())

        assertTrue(pendingDao.items.value.isEmpty())
        assertEquals(SyncStatus.SYNCED, reportDao.reports.value.single().syncStatus)
    }

    @Test
    fun `a duplicate report is dropped from the phone because the API already counts it`() = runTest {
        queueReport("client-1")
        api.syncHandler = { SyncResultDto(duplicates = listOf("client-1"), syncedAt = now) }

        engine.syncNow()

        assertTrue(pendingDao.items.value.isEmpty())
        assertTrue(reportDao.reports.value.isEmpty())
    }

    @Test
    fun `a rejected report is kept with its reason and never sent again`() = runTest {
        queueReport("client-1")
        api.syncHandler = { SyncResultDto(rejected = listOf(SyncRejectionDto("client-1", "reportedAt cannot be in the future.")), syncedAt = now) }

        engine.syncNow()
        engine.syncNow()

        val item = pendingDao.items.value.single()
        assertEquals(PendingSyncStatus.REJECTED, item.status)
        assertEquals("reportedAt cannot be in the future.", item.lastError)
        assertEquals(SyncStatus.REJECTED, reportDao.reports.value.single().syncStatus)
        assertEquals(1, api.callCount("sync"))
    }

    @Test
    fun `offline the items stay queued for a retry`() = runTest {
        queueReport("client-1")
        api.syncHandler = { offline() }

        assertEquals(SyncRunResult.RETRY, engine.syncNow())

        val item = pendingDao.items.value.single()
        assertEquals(PendingSyncStatus.RETRYING, item.status)
        assertEquals(1, item.retryCount)
        assertEquals(SyncStatus.PENDING, reportDao.reports.value.single().syncStatus)
    }

    @Test
    fun `a server fault or an expired session asks for a retry`() = runTest {
        queueReport("client-1")

        api.syncHandler = { httpError(503) }
        assertEquals(SyncRunResult.RETRY, engine.syncNow())
        api.syncHandler = { httpError(401) }
        assertEquals(SyncRunResult.RETRY, engine.syncNow())

        assertEquals(2, pendingDao.items.value.single().retryCount)
    }

    @Test
    fun `a retry after a lost answer sends the same client id, which the API accepts once`() = runTest {
        queueReport("client-1")
        api.syncHandler = { offline() }
        engine.syncNow()

        api.syncHandler = { SyncResultDto(accepted = listOf("client-1"), syncedAt = now) }
        engine.syncNow()

        assertEquals(listOf("client-1", "client-1"), api.syncBatches.map { it.reports.single().clientReportId })
        assertTrue(pendingDao.items.value.isEmpty())
    }

    @Test
    fun `settings that lost to a newer copy on the API are replaced by that copy`() = runTest {
        settingsDao.upsert(SettingsRules.defaults(USER, now).copy(languageCode = "af"))
        queue.enqueueSettings(USER, settingsUpdate("af"), now.minusSeconds(600))
        val changeId = pendingDao.items.value.single().syncId
        api.syncHandler = { SyncResultDto(superseded = listOf(changeId), settings = serverSettings("zu"), syncedAt = now) }

        engine.syncNow()

        assertTrue(pendingDao.items.value.isEmpty())
        assertEquals("zu", settingsDao.get(USER)?.languageCode)
    }

    @Test
    fun `the API's settings do not overwrite a newer change queued while the batch was in flight`() = runTest {
        settingsDao.upsert(SettingsRules.defaults(USER, now).copy(languageCode = "af"))
        queue.enqueueSettings(USER, settingsUpdate("af"), now)
        val changeId = pendingDao.items.value.single().syncId
        api.syncHandler = {
            queue.enqueueSettings(USER, settingsUpdate("zu"), now.plusSeconds(5))
            SyncResultDto(accepted = listOf(changeId), settings = serverSettings("af"), syncedAt = now)
        }

        engine.syncNow()

        val waiting = testJson.decodeFromString<SettingsChangeDto>(pendingDao.items.value.single().payloadJson)
        assertEquals("zu", waiting.languageCode)
        assertEquals("af", settingsDao.get(USER)?.languageCode)
    }

    @Test
    fun `the API's favourites replace the list on the phone once nothing else is waiting`() = runTest {
        favouriteDao.upsertAll(listOf(FavouriteAreaEntity("local-1", USER, KLOOF, "Work", 0, now)))
        queue.enqueueFavourite(KLOOF, "Work", removed = false)
        val changeId = pendingDao.items.value.single().syncId
        api.syncHandler = {
            SyncResultDto(
                accepted = listOf(changeId),
                favourites = listOf(FavouriteAreaDto("server-1", USER, KLOOF, "Work", 0, now)),
                syncedAt = now,
            )
        }

        engine.syncNow()

        assertEquals(listOf("server-1"), favouriteDao.favourites.value.map { it.favouriteId })
    }

    @Test
    fun `an unreadable item is rejected without blocking the rest`() = runTest {
        pendingDao.insert(PendingSyncEntity("broken", SyncEntityType.REPORT, "broken", "{not json", 0, now, PendingSyncStatus.PENDING, null))
        queueReport("client-1")
        api.syncHandler = { batch -> SyncResultDto(accepted = batch.reports.map { it.clientReportId }, syncedAt = now) }

        engine.syncNow()

        assertEquals(listOf("client-1"), api.syncBatches.single().reports.map { it.clientReportId })
        assertEquals(PendingSyncStatus.REJECTED, pendingDao.items.value.single().status)
        assertNull(pendingDao.items.value.firstOrNull { it.syncId == "client-1" })
    }

    private suspend fun queueReport(clientReportId: String) {
        val report = OutageReportEntity(clientReportId, clientReportId, USER, BEREA, OutageEventType.POWER_OFF, now, now, SyncStatus.PENDING)
        reportDao.insert(report)
        queue.enqueueReport(OutageReportCreateDto(BEREA, OutageEventType.POWER_OFF, now, now, clientReportId))
    }

    private fun settingsUpdate(language: String) =
        UserSettingsUpdateDto(BEREA, 30, true, "POWER_OFF,STAGE_CHANGE", language, "dark", false)

    private fun serverSettings(language: String) =
        UserSettingsDto(USER, BEREA, 30, true, "POWER_OFF,STAGE_CHANGE", language, "dark", false, now)

    private fun area(areaId: String) = AreaEntity(areaId, areaId, "eThekwini", "KwaZulu-Natal", "MUNICIPAL", null, null, true)

    private companion object {
        const val USER = "user-1"
        const val BEREA = "eth-berea"
        const val KLOOF = "eth-kloof"
    }
}
