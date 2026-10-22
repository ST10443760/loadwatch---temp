package com.example.loadwatch.fakes

import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.AreaStatusDao
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.dao.ScheduleEventDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import com.example.loadwatch.core.database.entity.AccuracySummaryEntity
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.AreaRefreshEntity
import com.example.loadwatch.core.database.entity.CurrentStageEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.FavouriteWithArea
import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import com.example.loadwatch.core.database.entity.RecentAreaEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

// In-memory DAOs whose observe flows re-emit on every write, as Room's do

class FakeAreaDao(initial: List<AreaEntity> = emptyList()) : AreaDao {
    val areas = MutableStateFlow(initial)

    override suspend fun upsertAll(areas: List<AreaEntity>) {
        this.areas.update { current -> current.filterNot { old -> areas.any { it.areaId == old.areaId } } + areas }
    }
    override suspend fun getById(areaId: String) = areas.value.firstOrNull { it.areaId == areaId }
    override fun observeById(areaId: String): Flow<AreaEntity?> = areas.map { list -> list.firstOrNull { it.areaId == areaId } }
    override fun search(query: String): Flow<List<AreaEntity>> = areas.map { list -> list.filter { it.name.contains(query, true) } }
    override suspend fun searchOnce(query: String, limit: Int) = areas.value.filter { it.name.contains(query, true) }.take(limit)
    override suspend fun count() = areas.value.size
    override suspend fun insertIfAbsent(area: AreaEntity) {
        if (getById(area.areaId) == null) areas.update { it + area }
    }
    override suspend fun updateSummary(areaId: String, name: String, municipality: String, province: String, supplyType: String): Int {
        val existing = getById(areaId) ?: return 0
        upsertAll(listOf(existing.copy(name = name, municipality = municipality, province = province, supplyType = supplyType)))
        return 1
    }
}

class FakeScheduleEventDao(private val transactions: FakeTransactionRunner? = null) : ScheduleEventDao {
    val events = MutableStateFlow<List<ScheduleEventEntity>>(emptyList())
    var writesOutsideTransaction = 0

    private fun recordWrite() {
        if (transactions != null && !transactions.inTransaction) writesOutsideTransaction++
    }

    override suspend fun upsertAll(events: List<ScheduleEventEntity>) {
        recordWrite()
        this.events.update { current -> current.filterNot { old -> events.any { it.scheduleId == old.scheduleId } } + events }
    }
    override fun observeWindow(areaId: String, from: Instant, to: Instant): Flow<List<ScheduleEventEntity>> =
        events.map { list -> list.filter { it.areaId == areaId && it.startAt < to && it.endAt > from }.sortedBy { it.startAt } }
    override fun observeCurrentOrNext(areaId: String, now: Instant): Flow<ScheduleEventEntity?> =
        events.map { list -> list.filter { it.areaId == areaId && it.endAt > now }.minByOrNull { it.startAt } }
    override suspend fun lastUpdatedAt(areaId: String) = events.value.filter { it.areaId == areaId }.maxOfOrNull { it.updatedAt }
    override suspend fun deleteEndedBefore(cutoff: Instant): Int {
        recordWrite()
        val before = events.value.size
        events.update { list -> list.filterNot { it.endAt < cutoff } }
        return before - events.value.size
    }
    override suspend fun deleteForArea(areaId: String) {
        recordWrite()
        events.update { list -> list.filterNot { it.areaId == areaId } }
    }
    override fun observeEndingAfter(areaId: String, from: Instant): Flow<List<ScheduleEventEntity>> =
        events.map { list -> list.filter { it.areaId == areaId && it.endAt > from }.sortedBy { it.startAt } }
}

class FakeAreaStatusDao : AreaStatusDao {
    val refreshes = MutableStateFlow<Map<String, AreaRefreshEntity>>(emptyMap())
    val accuracies = MutableStateFlow<Map<String, AccuracySummaryEntity>>(emptyMap())
    val stage = MutableStateFlow<CurrentStageEntity?>(null)

    override suspend fun upsertRefresh(refresh: AreaRefreshEntity) = refreshes.update { it + (refresh.areaId to refresh) }
    override suspend fun getRefresh(areaId: String) = refreshes.value[areaId]
    override fun observeRefresh(areaId: String): Flow<AreaRefreshEntity?> = refreshes.map { it[areaId] }
    override suspend fun upsertAccuracy(accuracy: AccuracySummaryEntity) = accuracies.update { it + (accuracy.areaId to accuracy) }
    override fun observeAccuracy(areaId: String): Flow<AccuracySummaryEntity?> = accuracies.map { it[areaId] }
    override suspend fun upsertCurrentStage(stage: CurrentStageEntity) {
        this.stage.value = stage
    }
    override fun observeCurrentStage(): Flow<CurrentStageEntity?> = stage
}

class FakeUserSettingsDao(initial: List<UserSettingsEntity> = emptyList()) : UserSettingsDao {
    val settings = MutableStateFlow(initial)

    override suspend fun upsert(settings: UserSettingsEntity) =
        this.settings.update { current -> current.filterNot { it.userId == settings.userId } + settings }
    override suspend fun get(userId: String) = settings.value.firstOrNull { it.userId == userId }
    override fun observe(userId: String): Flow<UserSettingsEntity?> = settings.map { list -> list.firstOrNull { it.userId == userId } }
    override suspend fun delete(userId: String) = settings.update { list -> list.filterNot { it.userId == userId } }
    override suspend fun deleteAll() {
        settings.value = emptyList()
    }
}

class FakeFavouriteAreaDao(private val areaDao: FakeAreaDao) : FavouriteAreaDao {
    val favourites = MutableStateFlow<List<FavouriteAreaEntity>>(emptyList())

    override suspend fun upsertAll(favourites: List<FavouriteAreaEntity>) =
        this.favourites.update { current -> current.filterNot { old -> favourites.any { it.favouriteId == old.favouriteId } } + favourites }
    override fun observe(userId: String): Flow<List<FavouriteAreaEntity>> =
        favourites.map { list -> list.filter { it.userId == userId }.sortedBy { it.sortOrder } }
    override suspend fun delete(userId: String, areaId: String): Int {
        val before = favourites.value.size
        favourites.update { list -> list.filterNot { it.userId == userId && it.areaId == areaId } }
        return before - favourites.value.size
    }
    override suspend fun deleteAllForUser(userId: String) = favourites.update { list -> list.filterNot { it.userId == userId } }
    override suspend fun deleteAll() {
        favourites.value = emptyList()
    }
    override fun observeWithAreas(userId: String): Flow<List<FavouriteWithArea>> =
        combine(observe(userId), areaDao.areas) { favourites, areas ->
            favourites.mapNotNull { favourite -> areas.firstOrNull { it.areaId == favourite.areaId }?.let { FavouriteWithArea(favourite, it) } }
        }
}

class FakeOutageReportDao : OutageReportDao {
    val reports = MutableStateFlow<List<OutageReportEntity>>(emptyList())

    override suspend fun insert(report: OutageReportEntity) = reports.update { it + report }
    override suspend fun upsertAll(reports: List<OutageReportEntity>) =
        this.reports.update { current -> current.filterNot { old -> reports.any { it.reportId == old.reportId } } + reports }
    override suspend fun getByClientReportId(userId: String, clientReportId: String) =
        reports.value.firstOrNull { it.userId == userId && it.clientReportId == clientReportId }
    override fun observeForArea(areaId: String, since: Instant): Flow<List<OutageReportEntity>> =
        reports.map { list -> list.filter { it.areaId == areaId && it.reportedAt >= since } }
    override suspend fun getByStatus(statuses: List<SyncStatus>) = reports.value.filter { it.syncStatus in statuses }
    override suspend fun updateSyncStatus(reportId: String, status: SyncStatus) =
        reports.update { list -> list.map { if (it.reportId == reportId) it.copy(syncStatus = status) else it } }
    override suspend fun deleteAll() {
        reports.value = emptyList()
    }
    override fun observeRecentForArea(userId: String, areaId: String, limit: Int): Flow<List<OutageReportEntity>> =
        reports.map { list -> list.filter { it.userId == userId && it.areaId == areaId }.sortedByDescending { it.clientCreatedAt }.take(limit) }
    override suspend fun getForAreaWithStatus(userId: String, areaId: String, statuses: List<SyncStatus>) =
        reports.value.filter { it.userId == userId && it.areaId == areaId && it.syncStatus in statuses }
    override suspend fun deleteByClientReportId(userId: String, clientReportId: String) =
        reports.update { list -> list.filterNot { it.userId == userId && it.clientReportId == clientReportId } }
}

class FakePendingSyncDao : PendingSyncDao {
    val items = MutableStateFlow<List<PendingSyncEntity>>(emptyList())

    override suspend fun insert(item: PendingSyncEntity) = items.update { it + item }
    override suspend fun update(item: PendingSyncEntity) = items.update { list -> list.map { if (it.syncId == item.syncId) item else it } }
    override suspend fun delete(syncId: String) = items.update { list -> list.filterNot { it.syncId == syncId } }
    override suspend fun getDue(now: Instant, rejected: PendingSyncStatus) = items.value.filter { it.status != rejected && it.nextAttemptAt <= now }
    override fun observeAll(): Flow<List<PendingSyncEntity>> = items
    override suspend fun deleteAll() {
        items.value = emptyList()
    }
}

class FakeRecentAreaDao(private val areaDao: FakeAreaDao) : RecentAreaDao {
    val recents = MutableStateFlow<List<RecentAreaEntity>>(emptyList())

    override suspend fun upsert(recent: RecentAreaEntity) = recents.update { list -> list.filterNot { it.areaId == recent.areaId } + recent }
    override suspend fun trimTo(keep: Int) = recents.update { list -> list.sortedByDescending { it.selectedAt }.take(keep) }
    override fun observeRecentAreas(): Flow<List<AreaEntity>> =
        combine(recents, areaDao.areas) { recents, areas ->
            recents.sortedByDescending { it.selectedAt }.mapNotNull { recent -> areas.firstOrNull { it.areaId == recent.areaId } }
        }
    override suspend fun deleteAll() {
        recents.value = emptyList()
    }
}
