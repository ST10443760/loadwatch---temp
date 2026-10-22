package com.example.loadwatch.feature.report

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.OutageReport
import com.example.loadwatch.data.repository.AccuracyScore
import com.example.loadwatch.data.repository.ReportRepository
import com.example.loadwatch.data.repository.ReportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSubmissionTest {

    private val repository = FakeReportRepository()

    @Test
    fun `an accepted report goes from sending to accepted`() = runTest {
        val submission = ReportSubmission(repository, this)

        submission.submit(AREA, OutageEventType.POWER_OFF)
        assertEquals(SubmissionStatus.Sending(OutageEventType.POWER_OFF), submission.status.value)

        runCurrent()
        assertEquals(SubmissionStatus.Accepted(OutageEventType.POWER_OFF), submission.status.value)
    }

    @Test
    fun `a 409 is shown as already reported, not as an error`() = runTest {
        repository.nextResult = ReportResult.AlreadyReported
        val submission = ReportSubmission(repository, this)

        submission.submit(AREA, OutageEventType.POWER_BACK_ON)
        runCurrent()

        assertEquals(SubmissionStatus.AlreadyReported(OutageEventType.POWER_BACK_ON), submission.status.value)
    }

    @Test
    fun `offline the report is kept on the phone as pending`() = runTest {
        repository.nextResult = ReportResult.SavedOffline(report(SyncStatus.PENDING))
        val submission = ReportSubmission(repository, this)

        submission.submit(AREA, OutageEventType.POWER_OFF)
        runCurrent()

        assertEquals(SubmissionStatus.SavedOffline(OutageEventType.POWER_OFF), submission.status.value)
    }

    @Test
    fun `a second tap while a report is in flight is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val submission = ReportSubmission(repository, this)

        assertTrue(submission.submit(AREA, OutageEventType.POWER_OFF))
        assertFalse("Same button tapped again", submission.submit(AREA, OutageEventType.POWER_OFF))
        runCurrent()
        assertFalse("Other button tapped while sending", submission.submit(AREA, OutageEventType.POWER_BACK_ON))

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(OutageEventType.POWER_OFF), repository.submitted)
        assertEquals(SubmissionStatus.Accepted(OutageEventType.POWER_OFF), submission.status.value)
        assertTrue("A new report can be sent once the first has finished", submission.submit(AREA, OutageEventType.POWER_BACK_ON))
    }

    @Test
    fun `a rejected report can be retried, which submits it again`() = runTest {
        repository.nextResult = ReportResult.Rejected(listOf("reportedAt"))
        val submission = ReportSubmission(repository, this)

        submission.submit(AREA, OutageEventType.POWER_OFF)
        runCurrent()
        assertEquals(SubmissionStatus.Rejected(OutageEventType.POWER_OFF, listOf("reportedAt")), submission.status.value)

        repository.nextResult = ReportResult.Accepted(report(SyncStatus.SYNCED))
        submission.retry(AREA)
        runCurrent()

        assertEquals(listOf(OutageEventType.POWER_OFF, OutageEventType.POWER_OFF), repository.submitted)
        assertEquals(SubmissionStatus.Accepted(OutageEventType.POWER_OFF), submission.status.value)
    }

    @Test
    fun `a 401 asks the user to sign in, and retry sends the pending reports`() = runTest {
        repository.nextResult = ReportResult.NotSignedIn
        val submission = ReportSubmission(repository, this)

        submission.submit(AREA, OutageEventType.POWER_OFF)
        runCurrent()
        assertEquals(SubmissionStatus.NotSignedIn(OutageEventType.POWER_OFF), submission.status.value)

        repository.retryResult = ReportResult.Accepted(report(SyncStatus.SYNCED))
        submission.retry(AREA)
        runCurrent()

        assertEquals(1, repository.retryCalls)
        assertEquals(SubmissionStatus.Accepted(OutageEventType.POWER_OFF), submission.status.value)
    }

    @Test
    fun `retry sending with nothing pending goes back to idle`() = runTest {
        val submission = ReportSubmission(repository, this)

        submission.retryUnsent(AREA)
        runCurrent()

        assertEquals(SubmissionStatus.Idle, submission.status.value)
    }

    @Test
    fun `a server error keeps the report and offers retry`() = runTest {
        repository.nextResult = ReportResult.Failed(DataFailure.SERVER)
        val submission = ReportSubmission(repository, this)

        submission.submit(AREA, OutageEventType.POWER_BACK_ON)
        runCurrent()

        assertEquals(SubmissionStatus.Failed(OutageEventType.POWER_BACK_ON, DataFailure.SERVER), submission.status.value)
    }

    private class FakeReportRepository : ReportRepository {
        var nextResult: ReportResult = ReportResult.Accepted(report(SyncStatus.SYNCED))
        var retryResult: ReportResult? = null
        var gate: CompletableDeferred<Unit>? = null
        val submitted = mutableListOf<OutageEventType>()
        var retryCalls = 0

        override fun observeRecentReports(areaId: String): Flow<List<OutageReport>> = flowOf(emptyList())
        override fun observeAccuracy(areaId: String): Flow<AccuracyScore?> = flowOf(null)
        override suspend fun refreshAccuracy(areaId: String) = true

        override suspend fun submit(areaId: String, eventType: OutageEventType): ReportResult {
            submitted += eventType
            gate?.await()
            return nextResult
        }

        override suspend fun retryUnsent(areaId: String): ReportResult? {
            retryCalls++
            return retryResult
        }
    }

    private companion object {
        const val AREA = "eth-berea"

        fun report(status: SyncStatus) =
            OutageReport("r-1", "c-1", AREA, OutageEventType.POWER_OFF, Instant.parse("2026-10-22T10:00:00Z"), status)
    }
}
