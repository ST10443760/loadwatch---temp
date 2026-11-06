package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.AreaDto
import com.example.loadwatch.core.network.dto.AreaSummaryDto
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Calls the API's area search and area detail endpoints and wraps each response in a [RemoteResult]. */
class AreaRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun searchAreas(query: String?, page: Int = 1): RemoteResult<List<AreaSummaryDto>> =
        safeApiCall(json, "searchAreas") { api.searchAreas(query, page) }

    suspend fun getArea(areaId: String): RemoteResult<AreaDto> =
        safeApiCall(json, "getArea") { api.getArea(areaId) }
}
