package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.database.dao.AreaStatusDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.data.mapper.toCreateDto
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.OutageReport
import com.example.loadwatch.data.remote.RemoteResult
import com.example.loadwatch.data.remote.ReportPostResult
import com.example.loadwatch.data.remote.ReportRemoteDataSource
import com.example.loadwatch.data.remote.ScheduleRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstReportRepository @Inject constructor(
    private val authSession: AuthSession,
    private val reportRemote: ReportRemoteDataSource,
    private val scheduleRemote: ScheduleRemoteDataSource,
    private val outageReportDao: OutageReportDao,
    private val areaStatusDao: AreaStatusDao,
    private val clock: Clock,
) : ReportRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentReports(areaId: String): Flow<List<OutageReport>> =
        authSession.signedInUserId.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                outageReportDao.observeRecentForArea(userId, areaId, RECENT_LIMIT).map { reports -> reports.map { it.toModel() } }
            }
        }

    override fun observeAccuracy(areaId: String): Flow<AccuracyScore?> =
        areaStatusDao.observeAccuracy(areaId).map { it?.let { AccuracyScore(it.accuracyScore, it.sampleSize) } }

    override suspend fun refreshAccuracy(areaId: String): Boolean =
        when (val result = scheduleRemote.getAccuracy(areaId)) {
            is RemoteResult.Success -> {
                areaStatusDao.upsertAccuracy(result.data.toEntity())
                true
            }
            else -> false
        }

    override suspend fun submit(areaId: String, eventType: OutageEventType): ReportResult {
        val userId = authSession.currentUserId() ?: return ReportResult.NotSignedIn
        val now = clock.instant()
        val clientReportId = UUID.randomUUID().toString()

        // Stored first, as PENDING under the client id, so the report survives a dropped connection or a crash
        val local = OutageReportEntity(
            reportId = clientReportId,
            clientReportId = clientReportId,
            userId = userId,
            areaId = areaId,
            eventType = eventType,
            reportedAt = now,
            clientCreatedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        outageReportDao.insert(local)
        Log.i(LogTags.API, "Submitting $eventType report for $areaId")
        return send(local)
    }

    override suspend fun retryUnsent(areaId: String): ReportResult? {
        val userId = authSession.currentUserId() ?: return ReportResult.NotSignedIn
        val unsent = outageReportDao.getForAreaWithStatus(userId, areaId, listOf(SyncStatus.PENDING, SyncStatus.RETRYING))
        if (unsent.isEmpty()) return null

        Log.i(LogTags.SYNC, "Retrying ${unsent.size} unsent reports for $areaId")
        var last: ReportResult? = null
        for (report in unsent) {
            outageReportDao.updateSyncStatus(report.reportId, SyncStatus.RETRYING)
            last = send(report)
            // Still no connection or no session: the rest would fail the same way
            if (last is ReportResult.SavedOffline || last is ReportResult.NotSignedIn) break
        }
        return last
    }

    // Sends a stored report. The same clientReportId is used on every attempt, so a retry after a dropped
    // response returns the original report instead of counting twice.
    private suspend fun send(report: OutageReportEntity): ReportResult =
        when (val result = reportRemote.submit(report.toCreateDto())) {
            is ReportPostResult.Accepted -> {
                val accepted = result.report.toEntity()
                outageReportDao.replaceWithServerCopy(accepted)
                Log.i(LogTags.API, "Report accepted for ${report.areaId}")
                // Show the effect of the report on the area's score
                refreshAccuracy(report.areaId)
                ReportResult.Accepted(accepted.toModel())
            }
            is ReportPostResult.AlreadyReported -> {
                // The API already counts this event; the local duplicate has nothing left to do
                outageReportDao.deleteByClientReportId(report.userId, report.clientReportId)
                Log.i(LogTags.API, "Report for ${report.areaId} was already reported")
                ReportResult.AlreadyReported
            }
            is ReportPostResult.Rejected ->
                if (result.status == 401) {
                    outageReportDao.updateSyncStatus(report.reportId, SyncStatus.PENDING)
                    ReportResult.NotSignedIn
                } else {
                    outageReportDao.updateSyncStatus(report.reportId, SyncStatus.REJECTED)
                    ReportResult.Rejected(result.problem?.errors?.keys?.toList().orEmpty())
                }
            is ReportPostResult.ServerError -> {
                outageReportDao.updateSyncStatus(report.reportId, SyncStatus.PENDING)
                ReportResult.Failed(DataFailure.SERVER)
            }
            is ReportPostResult.NetworkFailure -> {
                outageReportDao.updateSyncStatus(report.reportId, SyncStatus.PENDING)
                Log.i(LogTags.SYNC, "Report for ${report.areaId} saved on the device as PENDING")
                ReportResult.SavedOffline(report.copy(syncStatus = SyncStatus.PENDING).toModel())
            }
        }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
