package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.SyncBatchDto
import com.example.loadwatch.core.network.dto.SyncResultDto
import kotlinx.serialization.json.Json
import javax.inject.Inject

// POST /api/sync (bearer): the whole offline queue in one request
class SyncRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun sync(batch: SyncBatchDto): RemoteResult<SyncResultDto> = safeApiCall(json, "sync") { api.sync(batch) }
}
