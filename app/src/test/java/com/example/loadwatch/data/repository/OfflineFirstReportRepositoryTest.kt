package com.example.loadwatch.data.repository

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.network.dto.AccuracySummaryDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.OutageReportDto
import com.example.loadwatch.data.remote.ReportRemoteDataSource
import com.example.loadwatch.data.remote.ScheduleRemoteDataSource
import com.example.loadwatch.fakes.FakeAreaStatusDao
import com.example.loadwatch.fakes.FakeAuthSession
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.FakeOutageReportDao
import com.example.loadwatch.fakes.SAST
import com.example.loadwatch.fakes.offline
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Clock
import java.time.Instant

class OfflineFirstReportRepositoryTest {

    private val now = Instant.parse("2026-10-22T18:00:00Z")
    private val api = FakeLoadWatchApi()
    private val reportDao = FakeOutageReportDao()
    private val statusDao = FakeAreaStatusDao()
    private val repository = OfflineFirstReportRepository(
        authSession = FakeAuthSession(),
        reportRemote = ReportRemoteDataSource(api, testJson),
        scheduleRemote = ScheduleRemoteDataSource(api, testJson),
        outageReportDao = reportDao,
        areaStatusDao = statusDao,
        clock = Clock.fixed(now, SAST),
    )

    @Test
    fun `offline the report is stored as pending with its timestamps and a client id`() = runTest {
        api.createReportHandler = { offline() }

        val result = repository.submit(AREA, OutageEventType.POWER_OFF)

        assertTrue(result is ReportResult.SavedOffline)
        val stored = reportDao.reports.value.single()
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(now, stored.reportedAt)
        assertEquals(now, stored.clientCreatedAt)
        assertEquals(36, stored.clientReportId.length) // a UUID
    }

    @Test
    fun `an accepted report replaces the pending copy with the server's and refreshes accuracy`() = runTest {
        api.createReportHandler = { request -> Response.success(201, serverCopy(request)) }
        api.accuracyHandler = { AccuracySummaryDto(AREA, 0.86, 42, 72, now) }

        val result = repository.submit(AREA, OutageEventType.POWER_OFF)

        assertTrue(result is ReportResult.Accepted)
        val stored = reportDao.reports.value.single()
        assertEquals("server-id", stored.reportId)
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertEquals(42, statusDao.accuracies.value.getValue(AREA).sampleSize)
    }

    @Test
    fun `a 409 is already reported and leaves no duplicate on the phone`() = runTest {
        api.createReportHandler = { conflict() }

        val result = repository.submit(AREA, OutageEventType.POWER_BACK_ON)

        assertEquals(ReportResult.AlreadyReported, result)
        assertTrue(reportDao.reports.value.isEmpty())
    }

    @Test
    fun `a 400 marks the report rejected`() = runTest {
        api.createReportHandler = {
            Response.error(400, """{"status":400,"errors":{"reportedAt":["reportedAt cannot be in the future."]}}""".asProblem())
        }

        val result = repository.submit(AREA, OutageEventType.POWER_OFF)

        assertEquals(ReportResult.Rejected(listOf("reportedAt")), result)
        assertEquals(SyncStatus.REJECTED, reportDao.reports.value.single().syncStatus)
    }

    @Test
    fun `retrying a pending report reuses its client id so it is never counted twice`() = runTest {
        api.createReportHandler = { offline() }
        repository.submit(AREA, OutageEventType.POWER_OFF)
        val clientReportId = reportDao.reports.value.single().clientReportId

        val sent = mutableListOf<String>()
        api.createReportHandler = { request ->
            sent += request.clientReportId
            Response.success(200, serverCopy(request))
        }
        val result = repository.retryUnsent(AREA)

        assertTrue(result is ReportResult.Accepted)
        assertEquals(listOf(clientReportId), sent)
        assertEquals(SyncStatus.SYNCED, reportDao.reports.value.single().syncStatus)
    }

    private fun serverCopy(request: OutageReportCreateDto) = OutageReportDto(
        reportId = "server-id",
        clientReportId = request.clientReportId,
        userId = "user-1",
        areaId = request.areaId,
        eventType = request.eventType,
        reportedAt = request.reportedAt,
        clientCreatedAt = request.clientCreatedAt,
        syncStatus = SyncStatus.SYNCED,
    )

    private fun conflict(): Response<OutageReportDto> =
        Response.error(409, """{"status":409,"title":"Duplicate report","existingReportId":"original"}""".asProblem())

    private fun String.asProblem() = toResponseBody("application/problem+json".toMediaType())

    private companion object {
        const val AREA = "eth-berea"
    }
}
