package com.example.loadwatch.data.repository

import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.AreaRefreshEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.network.dto.NextEventDto
import com.example.loadwatch.core.network.dto.ScheduleEventDto
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.remote.ScheduleRemoteDataSource
import com.example.loadwatch.fakes.FakeAreaDao
import com.example.loadwatch.fakes.FakeAreaRepository
import com.example.loadwatch.fakes.FakeAreaStatusDao
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakeScheduleEventDao
import com.example.loadwatch.fakes.FakeTransactionRunner
import com.example.loadwatch.fakes.SAST
import com.example.loadwatch.fakes.httpError
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ScheduleRepositoryTest {

    // 20:00 SAST on 22 October
    private val now = Instant.parse("2026-10-22T18:00:00Z")
    private val todayStart = Instant.parse("2026-10-21T22:00:00Z") // midnight SAST

    private val transactions = FakeTransactionRunner()
    private val areaDao = FakeAreaDao(listOf(area(AREA), area(OTHER_AREA)))
    private val scheduleDao = FakeScheduleEventDao(transactions)
    private val statusDao = FakeAreaStatusDao()
    private val api = FakeLoadWatchApi()
    private val repository = ScheduleRepository(
        remote = ScheduleRemoteDataSource(api, testJson),
        areaDao = areaDao,
        scheduleEventDao = scheduleDao,
        areaStatusDao = statusDao,
        areaRepository = FakeAreaRepository(areaDao),
        transactionRunner = transactions,
        clock = Clock.fixed(now, SAST),
    )

    private val oldEvents = listOf(entity("old-1", AREA, 1), entity("old-2", AREA, 2))
    private val otherAreaEvent = entity("other-1", OTHER_AREA, 3)
    private val previousRefresh = AreaRefreshEntity(AREA, now.minusSeconds(3_600), todayStart, todayStart.plus(Duration.ofDays(7)), 2_000, null, null, null)

    @Before
    fun seedCache() = runTest {
        scheduleDao.events.value = oldEvents + otherAreaEvent
        statusDao.upsertRefresh(previousRefresh)
    }

    @Test
    fun `a refresh replaces this area's cached schedule in one transaction`() = runTest {
        val fresh = listOf(dto("new-1", 20), dto("new-2", 30), dto("new-3", 40))
        api.scheduleHandler = { _, _, _ -> fresh }
        api.nextEventHandler = { next(serverTime = now.plusSeconds(300)) }

        val result = repository.refresh(AREA)

        assertEquals(ScheduleRefreshResult.Refreshed, result)
        assertEquals(listOf("new-1", "new-2", "new-3"), scheduleDao.events.value.filter { it.areaId == AREA }.map { it.scheduleId }.sorted())
        assertTrue("Other areas are untouched", otherAreaEvent in scheduleDao.events.value)
        assertTrue(transactions.transactionCount >= 1)
        assertEquals("Every schedule write happened inside the transaction", 0, scheduleDao.writesOutsideTransaction)
    }

    @Test
    fun `a refresh records the window, the refresh time and the clock offset`() = runTest {
        api.scheduleHandler = { _, _, _ -> emptyList() }
        // The server is five minutes ahead of the device
        api.nextEventHandler = { next(serverTime = now.plusSeconds(300)) }

        repository.refresh(AREA)

        val (_, from, to) = api.scheduleRequests.single()
        assertEquals(todayStart, from)
        assertEquals(todayStart.plus(Duration.ofDays(7)), to)
        val refresh = statusDao.refreshes.value.getValue(AREA)
        assertEquals(now, refresh.lastRefreshedAt)
        assertEquals(300_000L, refresh.clockOffsetMillis)
    }

    @Test
    fun `a failed refresh keeps the old cache and refresh state`() = runTest {
        // Nothing configured: the fake API is offline

        val result = repository.refresh(AREA)

        assertEquals(ScheduleRefreshResult.Failed(DataFailure.OFFLINE), result)
        assertEquals(oldEvents + otherAreaEvent, scheduleDao.events.value)
        assertEquals(previousRefresh, statusDao.refreshes.value.getValue(AREA))
    }

    @Test
    fun `an unknown area is reported as not found and the cache is kept`() = runTest {
        api.scheduleHandler = { _, _, _ -> httpError(404, """{"status":404,"title":"Area not found"}""") }

        val result = repository.refresh(AREA)

        assertEquals(ScheduleRefreshResult.Failed(DataFailure.NOT_FOUND), result)
        assertEquals(oldEvents + otherAreaEvent, scheduleDao.events.value)
    }

    @Test
    fun `the window is never more than 31 days`() = runTest {
        api.scheduleHandler = { _, _, _ -> emptyList() }

        repository.refresh(AREA, days = 60)

        val (_, from, to) = api.scheduleRequests.single()
        assertEquals(Duration.ofDays(31), Duration.between(from, to))
    }

    @Test
    fun `without a next event response the previous clock offset is kept`() = runTest {
        api.scheduleHandler = { _, _, _ -> emptyList() }

        repository.refresh(AREA)

        assertEquals(2_000L, statusDao.refreshes.value.getValue(AREA).clockOffsetMillis)
    }

    private fun dto(id: String, hoursFromNow: Long) = ScheduleEventDto(
        scheduleId = id,
        areaId = AREA,
        stage = 2,
        startAt = now.plus(Duration.ofHours(hoursFromNow)),
        endAt = now.plus(Duration.ofHours(hoursFromNow)).plus(Duration.ofMinutes(150)),
        source = "SEED",
        updatedAt = now,
    )

    private fun next(serverTime: Instant) = NextEventDto(
        areaId = AREA,
        areaName = "Berea",
        currentState = SupplyState.ON,
        stage = 2,
        nextStartAt = now.plus(Duration.ofHours(20)),
        nextEndAt = now.plus(Duration.ofHours(20)).plus(Duration.ofMinutes(150)),
        serverTime = serverTime,
        updatedAt = now,
    )

    private fun entity(id: String, areaId: String, hoursFromNow: Long) = ScheduleEventEntity(
        scheduleId = id,
        areaId = areaId,
        stage = 2,
        startAt = now.plus(Duration.ofHours(hoursFromNow)),
        endAt = now.plus(Duration.ofHours(hoursFromNow)).plus(Duration.ofMinutes(150)),
        source = "SEED",
        updatedAt = now.minusSeconds(3_600),
    )

    private companion object {
        const val AREA = "eth-berea"
        const val OTHER_AREA = "eth-westville"

        fun area(id: String) = AreaEntity(id, id, "eThekwini", "KwaZulu-Natal", "MUNICIPAL", null, null, true)
    }
}
