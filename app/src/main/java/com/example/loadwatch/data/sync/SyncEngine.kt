package com.example.loadwatch.data.sync

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import com.example.loadwatch.core.network.dto.FavouriteChangeDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.SettingsChangeDto
import com.example.loadwatch.core.network.dto.SyncBatchDto
import com.example.loadwatch.core.network.dto.SyncResultDto
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.remote.RemoteResult
import com.example.loadwatch.data.remote.SyncRemoteDataSource
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.ReportRepository
import com.example.loadwatch.data.repository.SettingsRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncRunResult {
    // Everything that could be sent was sent, or nothing was waiting
    DONE,

    // Offline, signed out on the API, or a server fault: try again later
    RETRY,
}

// Sends the pending_sync queue to POST /api/sync and applies the answer. Every item is safe to send more than
// once: reports are keyed by clientReportId, a favourite change sets the final state, and settings use last write
// wins, so a sync cut off halfway loses nothing.
@Singleton
class SyncEngine @Inject constructor(
    private val authSession: AuthSession,
    private val pendingSyncDao: PendingSyncDao,
    private val remote: SyncRemoteDataSource,
    private val outageReportDao: OutageReportDao,
    private val favouriteAreaDao: FavouriteAreaDao,
    private val areaRepository: AreaRepository,
    private val reportRepository: ReportRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json,
    private val clock: Clock,
) {

    suspend fun syncNow(): SyncRunResult {
        val userId = authSession.currentUserId()
        if (userId == null) {
            Log.i(LogTags.SYNC, "Sync skipped: nobody is signed in")
            return SyncRunResult.DONE
        }

        // More than one round only when the queue holds more than one batch, or more was queued meanwhile. Each item
        // is sent at most once per run; one the API did not answer waits for the next run.
        val attempted = mutableSetOf<String>()
        repeat(MAX_ROUNDS) { round ->
            val due = pendingSyncDao.getDue(clock.instant()).filter { it.syncId !in attempted }
            if (due.isEmpty()) {
                Log.i(LogTags.SYNC, if (round == 0) "Nothing waiting to sync" else "Sync round finished; nothing new to send")
                return finish()
            }
            val batch = buildBatch(due)
            attempted += due.map { it.syncId }
            if (batch.items.isEmpty()) return@repeat
            Log.i(
                LogTags.SYNC,
                "Sending sync batch: ${batch.dto.reports.size} reports, ${batch.dto.favourites.size} favourite changes, " +
                    "settings ${if (batch.dto.settings != null) "included" else "unchanged"}",
            )

            when (val result = remote.sync(batch.dto)) {
                is RemoteResult.Success -> apply(userId, batch, result.data)
                is RemoteResult.NetworkFailure -> {
                    markForRetry(batch.items.values, "No connection")
                    Log.i(LogTags.SYNC, "Sync stopped: no connection; WorkManager will run it again when online")
                    return SyncRunResult.RETRY
                }
                is RemoteResult.HttpFailure -> when (result.status) {
                    400 -> {
                        // The whole batch was refused, which only a bug can cause; sending it again would fail the same way
                        markRejected(batch.items.values, result.problem?.title ?: "HTTP 400")
                        Log.e(LogTags.SYNC, "Sync batch refused with HTTP 400; ${batch.items.size} items marked rejected")
                    }
                    else -> {
                        markForRetry(batch.items.values, "HTTP ${result.status}")
                        Log.w(LogTags.SYNC, "Sync answered HTTP ${result.status}; will try again later")
                        return SyncRunResult.RETRY
                    }
                }
            }
        }
        return finish()
    }

    // Anything still waiting, such as an item the API did not answer, needs another run
    private suspend fun finish(): SyncRunResult =
        if (pendingSyncDao.getDue(clock.instant()).isEmpty()) {
            SyncRunResult.DONE
        } else {
            Log.i(LogTags.SYNC, "Some changes are still waiting; another sync will run later")
            SyncRunResult.RETRY
        }

    private data class Batch(val dto: SyncBatchDto, val items: Map<String, PendingSyncEntity>)

    // Turns queued rows into one request, keyed by the id the API answers with (the row's syncId). A row whose
    // payload cannot be read is rejected so it can never block the rest.
    private suspend fun buildBatch(due: List<PendingSyncEntity>): Batch {
        val reports = mutableListOf<OutageReportCreateDto>()
        val favourites = mutableListOf<FavouriteChangeDto>()
        var settings: SettingsChangeDto? = null
        val items = mutableMapOf<String, PendingSyncEntity>()

        for (item in due) {
            try {
                when (item.entityType) {
                    SyncEntityType.REPORT -> if (reports.size < MAX_ITEMS) {
                        reports += json.decodeFromString<OutageReportCreateDto>(item.payloadJson)
                        items[item.syncId] = item
                    }
                    SyncEntityType.FAVOURITE -> if (favourites.size < MAX_ITEMS) {
                        favourites += json.decodeFromString<FavouriteChangeDto>(item.payloadJson)
                        items[item.syncId] = item
                    }
                    SyncEntityType.SETTINGS -> {
                        settings = json.decodeFromString<SettingsChangeDto>(item.payloadJson)
                        items[item.syncId] = item
                    }
                    else -> markRejected(listOf(item), "Unknown type ${item.entityType}")
                }
            } catch (e: SerializationException) {
                markRejected(listOf(item), "Unreadable payload")
            } catch (e: IllegalArgumentException) {
                markRejected(listOf(item), "Unreadable payload")
            }
        }
        return Batch(SyncBatchDto(reports, favourites, settings), items)
    }

    private suspend fun apply(userId: String, batch: Batch, result: SyncResultDto) {
        val reportAreas = mutableSetOf<String>()
        for (id in result.accepted) {
            val item = batch.items[id] ?: continue
            pendingSyncDao.delete(item.syncId)
            if (item.entityType == SyncEntityType.REPORT) {
                outageReportDao.getByClientReportId(userId, item.entityId)?.let { report ->
                    outageReportDao.updateSyncStatus(report.reportId, SyncStatus.SYNCED)
                    reportAreas += report.areaId
                }
            }
        }
        for (id in result.duplicates) {
            val item = batch.items[id] ?: continue
            pendingSyncDao.delete(item.syncId)
            // The API already counts this event from this user, so the copy on the phone has nothing left to do
            if (item.entityType == SyncEntityType.REPORT) outageReportDao.deleteByClientReportId(userId, item.entityId)
        }
        for (id in result.superseded) {
            val item = batch.items[id] ?: continue
            pendingSyncDao.delete(item.syncId)
            Log.i(LogTags.SYNC, "Settings saved offline were older than the API's copy, which was kept (last write wins)")
        }
        for (rejection in result.rejected) {
            val item = batch.items[rejection.id] ?: continue
            markRejected(listOf(item), rejection.reason)
            if (item.entityType == SyncEntityType.REPORT) {
                outageReportDao.getByClientReportId(userId, item.entityId)?.let {
                    outageReportDao.updateSyncStatus(it.reportId, SyncStatus.REJECTED)
                }
            }
        }

        val answered = result.accepted + result.duplicates + result.superseded + result.rejected.map { it.id }
        val unanswered = batch.items.filterKeys { it !in answered }.values
        if (unanswered.isNotEmpty()) markForRetry(unanswered.toList(), "No answer from the API")

        Log.i(
            LogTags.SYNC,
            "Sync batch done: ${result.accepted.size} accepted, ${result.duplicates.size} duplicates, " +
                "${result.superseded.size} superseded, ${result.rejected.size} rejected",
        )

        // The API's copies win, unless a newer change was queued while this batch was in flight
        result.settings?.let { settings ->
            if (pendingSyncDao.countUnsent(SyncEntityType.SETTINGS) == 0) settingsRepository.applyServerSettings(settings)
        }
        result.favourites?.let { favourites ->
            if (pendingSyncDao.countUnsent(SyncEntityType.FAVOURITE) == 0) applyServerFavourites(userId, favourites)
        }
        reportAreas.forEach { reportRepository.refreshAccuracy(it) }
    }

    private suspend fun applyServerFavourites(userId: String, favourites: List<FavouriteAreaDto>) {
        // Favourites reference the area catalogue, so only keep those whose area can be cached
        val cachable = favourites.filter { areaRepository.ensureAreaCached(it.areaId) }
        favouriteAreaDao.replaceAllForUser(userId, cachable.map { it.toEntity() })
        Log.i(LogTags.SYNC, "Favourites now match the API: ${cachable.size} saved areas")
    }

    private suspend fun markForRetry(items: Collection<PendingSyncEntity>, reason: String) {
        items.forEach { item ->
            pendingSyncDao.update(item.copy(status = PendingSyncStatus.RETRYING, retryCount = item.retryCount + 1, lastError = reason))
        }
    }

    private suspend fun markRejected(items: Collection<PendingSyncEntity>, reason: String) {
        items.forEach { item ->
            pendingSyncDao.update(item.copy(status = PendingSyncStatus.REJECTED, lastError = reason))
            Log.w(LogTags.SYNC, "${item.entityType} ${item.entityId} rejected and will not be sent again: $reason")
        }
    }

    private companion object {
        // The API's limit per list in one batch
        const val MAX_ITEMS = 100
        const val MAX_ROUNDS = 5
    }
}
