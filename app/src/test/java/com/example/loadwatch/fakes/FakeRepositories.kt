package com.example.loadwatch.fakes

import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.AreaSearchResult
import com.example.loadwatch.data.repository.FavouriteResult
import com.example.loadwatch.data.repository.FavouritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// An area repository backed by the fake area table; every area in it counts as cached
class FakeAreaRepository(private val areaDao: FakeAreaDao) : AreaRepository {
    override suspend fun searchAreas(query: String): AreaSearchResult = AreaSearchResult.Unavailable(DataFailure.OFFLINE)
    override suspend fun ensureAreaCached(areaId: String): Boolean = areaDao.getById(areaId) != null
    override fun observeArea(areaId: String): Flow<Area?> = areaDao.observeById(areaId).map { it?.toModel() }
    override fun observeRecentAreas(): Flow<List<Area>> = flowOf(emptyList())
    override suspend fun recordRecentArea(areaId: String) = Unit
}

class FakeFavouritesRepository : FavouritesRepository {
    val favourites = MutableStateFlow<List<Favourite>>(emptyList())

    override fun observeFavourites(): Flow<List<Favourite>> = favourites
    override suspend fun refresh(): DataFailure? = null
    override suspend fun add(areaId: String, label: String?): FavouriteResult = FavouriteResult.Saved
    override suspend fun remove(areaId: String): FavouriteResult = FavouriteResult.Saved
    override suspend fun relabel(areaId: String, label: String?): FavouriteResult = FavouriteResult.Saved
}
