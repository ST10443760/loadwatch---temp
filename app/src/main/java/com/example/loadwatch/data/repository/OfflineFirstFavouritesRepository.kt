package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.model.FavouriteLabel
import com.example.loadwatch.data.remote.FavouritesRemoteDataSource
import com.example.loadwatch.data.remote.RemoteResult
import com.example.loadwatch.data.sync.SyncEntityType
import com.example.loadwatch.data.sync.SyncQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FavouritesRepository] that writes each change to Room first and sends it to the API. When offline it queues
 * the change in the sync queue instead.
 */
@Singleton
class OfflineFirstFavouritesRepository @Inject constructor(
    private val authSession: AuthSession,
    private val remote: FavouritesRemoteDataSource,
    private val favouriteAreaDao: FavouriteAreaDao,
    private val areaRepository: AreaRepository,
    private val syncQueue: SyncQueue,
    private val clock: Clock,
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
        // The API's list does not have the changes made offline yet; the sync worker brings both back in line
        if (syncQueue.hasUnsent(SyncEntityType.FAVOURITE)) {
            Log.i(LogTags.SYNC, "Kept the favourites on this phone; changes are still waiting to sync")
            return null
        }
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
                syncQueue.remove(SyncEntityType.FAVOURITE, areaId)
                Log.i(LogTags.API, "Favourite added: $areaId")
                FavouriteResult.Saved
            }
            is RemoteResult.NetworkFailure -> addOffline(userId, areaId, validLabel)
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
                in 500..599 -> addOffline(userId, areaId, validLabel)
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
                syncQueue.remove(SyncEntityType.FAVOURITE, areaId)
                Log.i(LogTags.API, "Favourite removed: $areaId")
                FavouriteResult.Saved
            }
            is RemoteResult.NetworkFailure -> removeOffline(userId, areaId)
            is RemoteResult.HttpFailure -> when (result.status) {
                401 -> FavouriteResult.NotSignedIn
                in 500..599 -> removeOffline(userId, areaId)
                else -> FavouriteResult.Failed(DataFailure.SERVER)
            }
        }
    }

    override suspend fun relabel(areaId: String, label: String?): FavouriteResult {
        if (FavouriteLabel.validate(label) == FavouriteLabel.Validation.TooLong) return FavouriteResult.LabelTooLong
        val userId = authSession.currentUserId() ?: return FavouriteResult.NotSignedIn
        val previousLabel = favouriteAreaDao.observe(userId).first().firstOrNull { it.areaId == areaId }?.label

        val removed = remove(areaId)
        if (removed != FavouriteResult.Saved && removed != FavouriteResult.SavedOffline) return removed

        val added = add(areaId, label)
        if (added != FavouriteResult.Saved && added != FavouriteResult.SavedOffline && previousLabel != null) {
            // Put the old favourite back rather than lose it
            Log.w(LogTags.API, "Relabel failed for $areaId; restoring the previous label")
            add(areaId, previousLabel)
        }
        return added
    }

    // Shows the favourite straight away and queues it. Its local id is replaced by the API's when the sync answers.
    private suspend fun addOffline(userId: String, areaId: String, label: String): FavouriteResult {
        val lastSortOrder = favouriteAreaDao.observe(userId).first().maxOfOrNull { it.sortOrder }
        favouriteAreaDao.upsertAll(
            listOf(
                FavouriteAreaEntity(
                    favouriteId = "local-${UUID.randomUUID()}",
                    userId = userId,
                    areaId = areaId,
                    label = label,
                    sortOrder = (lastSortOrder ?: -1) + 1,
                    createdAt = clock.instant(),
                ),
            ),
        )
        syncQueue.enqueueFavourite(areaId, label, removed = false)
        Log.i(LogTags.SYNC, "Favourite $areaId saved on the phone; it will sync when online")
        return FavouriteResult.SavedOffline
    }

    private suspend fun removeOffline(userId: String, areaId: String): FavouriteResult {
        favouriteAreaDao.delete(userId, areaId)
        syncQueue.enqueueFavourite(areaId, label = null, removed = true)
        Log.i(LogTags.SYNC, "Favourite $areaId removed on the phone; it will sync when online")
        return FavouriteResult.SavedOffline
    }

    private suspend fun isSavedLocally(userId: String, areaId: String): Boolean =
        favouriteAreaDao.observe(userId).first().any { it.areaId == areaId }
}
