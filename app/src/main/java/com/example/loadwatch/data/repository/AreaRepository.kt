package com.example.loadwatch.data.repository

import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import kotlinx.coroutines.flow.Flow

/**
 * The result of an area search: the areas found, and whether they came from the offline cache, or why nothing
 * could be found.
 */
sealed interface AreaSearchResult {
    // fromCache is true when the API could not be reached and the results come from the Room catalogue
    data class Found(val areas: List<Area>, val fromCache: Boolean) : AreaSearchResult

    data class Unavailable(val failure: DataFailure) : AreaSearchResult
}

/** Area search, the cached area catalogue and the recently used areas, offline first. */
interface AreaRepository {

    // Searches by suburb or municipality, caching what the API returns. Falls back to the cached catalogue
    // when the API cannot be reached.
    suspend fun searchAreas(query: String): AreaSearchResult

    // Makes sure the area is in the Room catalogue, fetching its full details when online.
    // Returns false only when it is neither cached nor reachable.
    suspend fun ensureAreaCached(areaId: String): Boolean

    fun observeArea(areaId: String): Flow<Area?>

    fun observeRecentAreas(): Flow<List<Area>>

    suspend fun recordRecentArea(areaId: String)
}
