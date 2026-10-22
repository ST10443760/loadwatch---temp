package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.FavouriteAreaCreateDto
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

// GET, POST and DELETE /api/users/{userId}/favourites (bearer)
class FavouritesRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun getFavourites(userId: String): RemoteResult<List<FavouriteAreaDto>> =
        safeApiCall(json, "getFavourites") { api.getFavourites(userId) }

    // 201 with the favourite, or HttpFailure(409) when the area is already saved
    suspend fun addFavourite(userId: String, areaId: String, label: String): RemoteResult<FavouriteAreaDto> =
        safeApiCall(json, "addFavourite") { api.addFavourite(userId, FavouriteAreaCreateDto(areaId, label)) }

    // 204 when removed; a 404 means it was already gone, which counts as success
    suspend fun removeFavourite(userId: String, areaId: String): RemoteResult<Unit> =
        safeApiCall(json, "removeFavourite") {
            val response = api.removeFavourite(userId, areaId)
            if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
        }
}
