package com.example.loadwatch.data.history

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.network.dto.AreaHistoryDto
import com.example.loadwatch.core.network.dto.HistoryDayDto
import com.example.loadwatch.core.network.dto.HistoryOutageDto
import com.example.loadwatch.core.network.dto.HistoryReportDto
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.remote.HistoryRemoteDataSource
import com.example.loadwatch.data.repository.HistoryRepository
import com.example.loadwatch.fakes.FakeHistoryDao
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.SAST
import com.example.loadwatch.fakes.httpError
import com.example.loadwatch.fakes.offline
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth

class HistoryRepositoryTest {

    private val now = Instant.parse("2026-11-06T08:00:00Z")
    private val november = YearMonth.of(2026, 11)
    private val api = FakeLoadWatchApi()
    private val dao = FakeHistoryDao()
    private val repository = HistoryRepository(HistoryRemoteDataSource(api, testJson), dao, Clock.fixed(now, SAST))

    @Test
    fun `a month never fetched has no fetch time, so it is not mistaken for an empty month`() = runTest {
        val month = repository.observeMonth(AREA, november).first()

        assertNull(month.fetchedAt)
        assertTrue(month.outages.isEmpty())
    }

    @Test
    fun `a refresh asks for the month as yyyy-MM and caches its outages and reports`() = runTest {
        api.historyHandler = { areaId, month -> history(areaId, month) }

        assertNull(repository.refresh(AREA, november))

        assertEquals(listOf(AREA to "2026-11"), api.historyRequests)
        val month = repository.observeMonth(AREA, november).first()
        assertEquals(now, month.fetchedAt)
        assertEquals(listOf("o1"), month.outages.map { it.scheduleId })
        assertEquals(listOf("r1"), month.reports.map { it.reportId })
    }

    @Test
    fun `a later refresh replaces the month rather than adding to it`() = runTest {
        api.historyHandler = { areaId, month -> history(areaId, month) }
        repository.refresh(AREA, november)

        api.historyHandler = { areaId, month -> AreaHistoryDto(areaId, month, emptyList()) }
        repository.refresh(AREA, november)

        val month = repository.observeMonth(AREA, november).first()
        assertTrue(month.outages.isEmpty())
        assertTrue(month.reports.isEmpty())
        assertEquals(now, month.fetchedAt)
    }

    @Test
    fun `offline the cached month stays and the failure is reported`() = runTest {
        api.historyHandler = { areaId, month -> history(areaId, month) }
        repository.refresh(AREA, november)

        api.historyHandler = { _, _ -> offline() }

        assertEquals(DataFailure.OFFLINE, repository.refresh(AREA, november))
        assertEquals(1, repository.observeMonth(AREA, november).first().outages.size)
    }

    @Test
    fun `an unknown area is not found and a server fault is a server failure`() = runTest {
        api.historyHandler = { _, _ -> httpError(404) }
        assertEquals(DataFailure.NOT_FOUND, repository.refresh(AREA, november))

        api.historyHandler = { _, _ -> httpError(500) }
        assertEquals(DataFailure.SERVER, repository.refresh(AREA, november))
    }

    @Test
    fun `months and areas are cached separately`() = runTest {
        api.historyHandler = { areaId, month -> history(areaId, month) }
        repository.refresh(AREA, november)

        assertNull(repository.observeMonth(AREA, november.minusMonths(1)).first().fetchedAt)
        assertNull(repository.observeMonth("eth-kloof", november).first().fetchedAt)
    }

    private fun history(areaId: String, month: String) = AreaHistoryDto(
        areaId = areaId,
        month = month,
        days = listOf(
            HistoryDayDto(
                date = "2026-11-03",
                outages = listOf(HistoryOutageDto("o1", 2, Instant.parse("2026-11-03T06:00:00Z"), Instant.parse("2026-11-03T08:30:00Z"), 1)),
                reports = listOf(HistoryReportDto("r1", OutageEventType.POWER_OFF, Instant.parse("2026-11-03T06:10:00Z"))),
            ),
        ),
    )

    private companion object {
        const val AREA = "eth-berea"
    }
}
