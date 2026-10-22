package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.AccuracySummaryDto
import com.example.loadwatch.core.network.dto.CurrentStageDto
import com.example.loadwatch.core.network.dto.NextEventDto
import com.example.loadwatch.core.network.dto.ScheduleEventDto
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject

// The public schedule endpoints Home and Schedule refresh from (all anonymous)
class ScheduleRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun getSchedule(areaId: String, from: Instant, to: Instant): RemoteResult<List<ScheduleEventDto>> =
        safeApiCall(json, "getSchedule") { api.getSchedule(areaId, from, to) }

    suspend fun getNextEvent(areaId: String): RemoteResult<NextEventDto> =
        safeApiCall(json, "getNextEvent") { api.getNextEvent(areaId) }

    suspend fun getCurrentStage(): RemoteResult<CurrentStageDto> =
        safeApiCall(json, "getCurrentStage") { api.getCurrentStage() }

    suspend fun getAccuracy(areaId: String): RemoteResult<AccuracySummaryDto> =
        safeApiCall(json, "getAreaAccuracy") { api.getAreaAccuracy(areaId) }
}
