package com.example.loadwatch.data.sync

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import com.example.loadwatch.core.network.dto.FavouriteChangeDto
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.SettingsChangeDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// What a pending_sync row holds, stored in its entityType column
object SyncEntityType {
    const val REPORT = "OutageReport"
    const val FAVOURITE = "FavouriteArea"
    const val SETTINGS = "UserSettings"
}

// Writes made while offline wait here, in Room, until SyncEngine sends them. Each write asks for a background sync,
// which WorkManager runs as soon as the phone is online. A newer change to the same favourite or to the settings
// replaces the older one still waiting, so only the latest state is sent.
@Singleton
class SyncQueue @Inject constructor(
    private val pendingSyncDao: PendingSyncDao,
    private val scheduler: SyncScheduler,
    private val json: Json,
    private val clock: Clock,
) {
    // Items still to be sent; rejected ones are not counted because they will never be sent again
    fun observeUnsentCount(): Flow<Int> =
        pendingSyncDao.observeAll()
            .map { items -> items.count { it.status != PendingSyncStatus.REJECTED } }
            .distinctUntilChanged()

    // The ids of one type's items still to be sent, such as the area ids of favourite changes
    fun observeUnsentIds(entityType: String): Flow<Set<String>> =
        pendingSyncDao.observeAll()
            .map { items -> items.filter { it.entityType == entityType && it.status != PendingSyncStatus.REJECTED }.map { it.entityId }.toSet() }
            .distinctUntilChanged()

    suspend fun hasUnsent(entityType: String): Boolean = pendingSyncDao.countUnsent(entityType) > 0

    // The report's clientReportId doubles as the queue id, so queueing the same report twice keeps one row and the
    // API can never store it twice
    suspend fun enqueueReport(report: OutageReportCreateDto) {
        add(SyncEntityType.REPORT, report.clientReportId, json.encodeToString(report), syncId = report.clientReportId)
    }

    suspend fun enqueueFavourite(areaId: String, label: String?, removed: Boolean) {
        val syncId = UUID.randomUUID().toString()
        val change = FavouriteChangeDto(changeId = syncId, areaId = areaId, label = label, removed = removed, changedAt = clock.instant())
        add(SyncEntityType.FAVOURITE, areaId, json.encodeToString(change), syncId)
    }

    // changedAt is when the user saved, which decides last write wins on the API
    suspend fun enqueueSettings(userId: String, settings: UserSettingsUpdateDto, changedAt: Instant) {
        val syncId = UUID.randomUUID().toString()
        val change = SettingsChangeDto(
            changeId = syncId,
            changedAt = changedAt,
            defaultAreaId = settings.defaultAreaId,
            notificationLeadMinutes = settings.notificationLeadMinutes,
            notificationsEnabled = settings.notificationsEnabled,
            enabledAlertTypes = settings.enabledAlertTypes,
            languageCode = settings.languageCode,
            themePreference = settings.themePreference,
            biometricEnabled = settings.biometricEnabled,
        )
        add(SyncEntityType.SETTINGS, userId, json.encodeToString(change), syncId)
    }

    // Drops an item that reached the API another way, such as a direct retry that succeeded
    suspend fun remove(entityType: String, entityId: String) {
        pendingSyncDao.deleteForEntity(entityType, entityId)
    }

    private suspend fun add(entityType: String, entityId: String, payloadJson: String, syncId: String) {
        pendingSyncDao.replaceForEntity(
            PendingSyncEntity(
                syncId = syncId,
                entityType = entityType,
                entityId = entityId,
                payloadJson = payloadJson,
                retryCount = 0,
                nextAttemptAt = clock.instant(),
                status = PendingSyncStatus.PENDING,
                lastError = null,
            ),
        )
        Log.i(LogTags.SYNC, "Queued $entityType $entityId to sync when online")
        scheduler.requestSync("$entityType queued")
    }
}
