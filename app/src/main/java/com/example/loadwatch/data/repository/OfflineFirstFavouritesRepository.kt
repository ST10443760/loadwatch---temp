package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.model.FavouriteLabel
import com.example.loadwatch.data.remote.FavouritesRemoteDataSource
import com.example.loadwatch.data.remote.RemoteResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFirstFavouritesRepository @Inject constructor(
    private val authSession: AuthSession,
    private val remote: FavouritesRemoteDataSource,
    private val favouriteAreaDao: FavouriteAreaDao,
    private val areaRepository: AreaRepository,
) : FavouritesRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFavourites(): Flow<List<Favourite>> =
        authSession.signedInUserId.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                favouriteAreaDao.observeWithAreas(userId).map { favourites -> favourites.map { it.toModel() } }
            }
        }

    override suspend fun refresh(): DataFailure? {
        val userId = authSession.currentUserId() ?: return DataFailure.NOT_SIGNED_IN
        return when (val result = remote.getFavourites(userId)) {
            is RemoteResult.Success -> {
                // Favourites reference the area catalogue, so only keep those whose area can be cached
                val cachable = result.data.filter { areaRepository.ensureAreaCached(it.areaId) }
                favouriteAreaDao.replaceAllForUser(userId, cachable.map { it.toEntity() })
                Log.i(LogTags.API, "Favourites refreshed: ${cachable.size} saved areas")
                null
            }
            is RemoteResult.NetworkFailure -> DataFailure.OFFLINE
            is RemoteResult.HttpFailure -> if (result.status == 401) DataFailure.NOT_SIGNED_IN else DataFailure.SERVER
        }
    }

    override suspend fun add(areaId: String, label: String?): FavouriteResult {
        val validLabel = when (val validation = FavouriteLabel.validate(label)) {
            FavouriteLabel.Validation.TooLong -> return FavouriteResult.LabelTooLong
            is FavouriteLabel.Validation.Valid -> validation.label
        }
        val userId = authSession.currentUserId() ?: return FavouriteResult.NotSignedIn
        if (isSavedLocally(userId, areaId)) return FavouriteResult.AlreadyFavourite
        if (!areaRepository.ensureAreaCached(areaId)) return FavouriteResult.Failed(DataFailure.OFFLINE)

        return when (val result = remote.addFavourite(userId, areaId, validLabel)) {
            is RemoteResult.Success -> {
                favouriteAreaDao.upsertAll(listOf(result.data.toEntity()))
                Log.i(LogTags.API, "Favourite added: $areaId")
                FavouriteResult.Saved
            }
            is RemoteResult.NetworkFailure -> FavouriteResult.Failed(DataFailure.OFFLINE)
            is RemoteResult.HttpFailure -> when (result.status) {
                409 -> {
                    // Saved from another device: pull the server's list so this one shows it too
                    refresh()
                    FavouriteResult.AlreadyFavourite
                }
                400 -> if (result.problem?.errors?.containsKey("label") == true) {
                    FavouriteResult.LabelTooLong
                } else {
                    FavouriteResult.Failed(DataFailure.SERVER)
                }
                401 -> FavouriteResult.NotSignedIn
                else -> FavouriteResult.Failed(DataFailure.SERVER)
            }
        }
    }

    override suspend fun remove(areaId: String): FavouriteResult {
        val userId = authSession.currentUserId() ?: return FavouriteResult.NotSignedIn
        return when (val result = remote.removeFavourite(userId, areaId)) {
            is RemoteResult.Success -> {
                // Only the favourite row goes; the area and its schedule stay cached (FR-FAV-03)
                favouriteAreaDao.delete(userId, areaId)
                Log.i(LogTags.API, "Favourite removed: $areaId")
                FavouriteResult.Saved
            }
            is RemoteResult.NetworkFailure -> FavouriteResult.Failed(DataFailure.OFFLINE)
            is RemoteResult.HttpFailure ->
                if (result.status == 401) FavouriteResult.NotSignedIn else FavouriteResult.Failed(DataFailure.SERVER)
        }
    }

    override suspend fun relabel(areaId: String, label: String?): FavouriteResult {
        if (FavouriteLabel.validate(label) == FavouriteLabel.Validation.TooLong) return FavouriteResult.LabelTooLong
        val userId = authSession.currentUserId() ?: return FavouriteResult.NotSignedIn
        val previousLabel = favouriteAreaDao.observe(userId).first().firstOrNull { it.areaId == areaId }?.label

        val removed = remove(areaId)
        if (removed != FavouriteResult.Saved) return removed

        val added = add(areaId, label)
        if (added != FavouriteResult.Saved && previousLabel != null) {
            // Put the old favourite back rather than lose it
            Log.w(LogTags.API, "Relabel failed for $areaId; restoring the previous label")
            add(areaId, previousLabel)
        }
        return added
    }

    private suspend fun isSavedLocally(userId: String, areaId: String): Boolean =
        favouriteAreaDao.observe(userId).first().any { it.areaId == areaId }
}
