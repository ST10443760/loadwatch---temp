package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.dao.HistoryDao
import com.example.loadwatch.core.database.entity.HistoryMonthEntity
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.HistoryMonth
import com.example.loadwatch.data.remote.HistoryRemoteDataSource
import com.example.loadwatch.data.remote.RemoteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

// An area's outage history, a month at a time (user-defined feature 5). Each month is cached in Room, so a month
// seen once can be opened again offline.
@Singleton
class HistoryRepository @Inject constructor(
    private val remote: HistoryRemoteDataSource,
    private val historyDao: HistoryDao,
    private val clock: Clock,
) {

    fun observeMonth(areaId: String, month: YearMonth): Flow<HistoryMonth> {
        val key = month.toString()
        return combine(
            historyDao.observeOutages(areaId, key),
            historyDao.observeReports(areaId, key),
            historyDao.observeMonth(areaId, key),
        ) { outages, reports, fetched ->
            HistoryMonth(
                areaId = areaId,
                month = month,
                outages = outages.map { it.toModel() },
                reports = reports.map { it.toModel() },
                fetchedAt = fetched?.fetchedAt,
            )
        }
    }

    // Replaces the cached month with the API's copy. Returns the failure, or null on success; on failure the
    // cached copy, if any, stays.
    suspend fun refresh(areaId: String, month: YearMonth): DataFailure? {
        val key = month.toString()
        return when (val result = remote.getMonth(areaId, key)) {
            is RemoteResult.Success -> {
                val days = result.data.days
                historyDao.replaceMonth(
                    month = HistoryMonthEntity(areaId, key, clock.instant()),
                    outages = days.flatMap { day -> day.outages.map { it.toEntity(areaId, key) } },
                    reports = days.flatMap { day -> day.reports.map { it.toEntity(areaId, key) } },
                )
                Log.i(LogTags.ROOM, "Cached $key history for $areaId: ${days.size} days with outages or reports")
                null
            }
            is RemoteResult.NetworkFailure -> DataFailure.OFFLINE
            is RemoteResult.HttpFailure -> if (result.status == 404) DataFailure.NOT_FOUND else DataFailure.SERVER
        }
    }
}
