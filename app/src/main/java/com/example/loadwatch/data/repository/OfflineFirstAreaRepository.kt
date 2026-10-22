package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.entity.RecentAreaEntity
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.remote.AreaRemoteDataSource
import com.example.loadwatch.data.remote.RemoteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstAreaRepository @Inject constructor(
    private val remote: AreaRemoteDataSource,
    private val areaDao: AreaDao,
    private val recentAreaDao: RecentAreaDao,
) : AreaRepository {

    override suspend fun searchAreas(query: String): AreaSearchResult =
        when (val result = remote.searchAreas(query)) {
            is RemoteResult.Success -> {
                areaDao.cacheSummaries(result.data.map { it.toEntity() })
                AreaSearchResult.Found(result.data.map { it.toModel() }, fromCache = false)
            }
            is RemoteResult.NetworkFailure -> searchCache(query, DataFailure.OFFLINE)
            is RemoteResult.HttpFailure -> searchCache(query, DataFailure.SERVER)
        }

    private suspend fun searchCache(query: String, failure: DataFailure): AreaSearchResult {
        val cached = areaDao.searchOnce(query, SEARCH_LIMIT)
        Log.i(LogTags.ROOM, "Area search fell back to the cached catalogue: ${cached.size} matches")
        return if (cached.isEmpty()) {
            AreaSearchResult.Unavailable(failure)
        } else {
            AreaSearchResult.Found(cached.map { it.toModel() }, fromCache = true)
        }
    }

    override suspend fun ensureAreaCached(areaId: String): Boolean =
        when (val result = remote.getArea(areaId)) {
            is RemoteResult.Success -> {
                areaDao.upsertAll(listOf(result.data.toEntity()))
                true
            }
            else -> areaDao.getById(areaId) != null
        }

    override fun observeArea(areaId: String): Flow<Area?> =
        areaDao.observeById(areaId).map { it?.toModel() }

    override fun observeRecentAreas(): Flow<List<Area>> =
        recentAreaDao.observeRecentAreas().map { areas -> areas.map { it.toModel() } }

    override suspend fun recordRecentArea(areaId: String) {
        recentAreaDao.record(RecentAreaEntity(areaId, Instant.now()), keep = RECENT_LIMIT)
    }

    private companion object {
        const val SEARCH_LIMIT = 20
        const val RECENT_LIMIT = 5
    }
}
