package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.AreaHistoryDto
import kotlinx.serialization.json.Json
import javax.inject.Inject

// GET /api/areas/{areaId}/history?month=yyyy-MM (anonymous)
class HistoryRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun getMonth(areaId: String, month: String): RemoteResult<AreaHistoryDto> =
        safeApiCall(json, "getHistory") { api.getHistory(areaId, month) }
}
