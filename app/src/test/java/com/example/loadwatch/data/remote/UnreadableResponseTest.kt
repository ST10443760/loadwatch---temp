package com.example.loadwatch.data.remote

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.OutageReportDto
import com.example.loadwatch.fakes.FakeLoadWatchApi
import com.example.loadwatch.fakes.testJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

// A response the app cannot read is shown as a server problem, never a crash
class UnreadableResponseTest {

    @Test
    fun `a malformed date in a response is a serialization error, not a crash elsewhere`() {
        val body = """{"reportId":"r","clientReportId":"c","userId":"u","areaId":"a","eventType":"POWER_OFF",
            "reportedAt":"yesterday","clientCreatedAt":"2026-11-06T10:00:00Z","syncStatus":"SYNCED"}"""

        assertThrows(SerializationException::class.java) { testJson.decodeFromString<OutageReportDto>(body) }
    }

    @Test
    fun `an unreadable body becomes a server failure`() = runTest {
        val result = safeApiCall(testJson, "test") { throw SerializationException("Unexpected JSON") }

        assertEquals(RemoteResult.HttpFailure(UNREADABLE_RESPONSE, null), result)
    }

    @Test
    fun `an unreadable report answer keeps the report for a retry`() = runTest {
        val api = FakeLoadWatchApi().apply { createReportHandler = { throw SerializationException("Unexpected JSON") } }
        val now = Instant.parse("2026-11-06T10:00:00Z")

        val result = ReportRemoteDataSource(api, testJson).submit(OutageReportCreateDto("eth-berea", OutageEventType.POWER_OFF, now, now, "c"))

        assertEquals(ReportPostResult.ServerError(UNREADABLE_RESPONSE), result)
    }
}
