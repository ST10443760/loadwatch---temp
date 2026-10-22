package com.example.loadwatch.data.repository

import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import kotlinx.coroutines.flow.Flow

sealed interface FavouriteResult {
    data object Saved : FavouriteResult

    // 409 from the API, or already saved on this device
    data object AlreadyFavourite : FavouriteResult

    // Longer than FavouriteLabel.MAX_LENGTH characters; nothing was sent
    data object LabelTooLong : FavouriteResult

    data object NotSignedIn : FavouriteResult

    data class Failed(val failure: DataFailure) : FavouriteResult
}

// The signed-in user's saved areas (FR-FAV-01 to 03), cached in Room. Removing a favourite never deletes the
// area or its schedule.
interface FavouritesRepository {

    fun observeFavourites(): Flow<List<Favourite>>

    // Mirrors the API's list into Room. Returns the failure, or null on success.
    suspend fun refresh(): DataFailure?

    suspend fun add(areaId: String, label: String?): FavouriteResult

    suspend fun remove(areaId: String): FavouriteResult

    // The API has no update for favourites, so a new label is a remove and re-add
    suspend fun relabel(areaId: String, label: String?): FavouriteResult
}
