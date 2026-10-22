package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toUpdateDto
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.remote.RemoteResult
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaveSettingsResult {
    // Saved on the device and on the API
    data object Saved : SaveSettingsResult

    // Saved on the device, but the API could not be updated; retrying sends it again
    data class SavedOnDeviceOnly(val failure: DataFailure) : SaveSettingsResult

    // A value broke the API's rules, so nothing was saved; fields are the JSON field names
    data class Rejected(val fields: List<String>) : SaveSettingsResult

    // Nothing could be saved
    data class Failed(val failure: DataFailure) : SaveSettingsResult
}

@Singleton
class SettingsRepository @Inject constructor(
    private val authSession: AuthSession,
    private val local: SettingsLocalDataSource,
    private val remote: SettingsRemoteDataSource,
    private val areaRepository: AreaRepository,
) {

    // The signed-in user's default area, which Home shows and S05 switches
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDefaultArea(): Flow<Area?> =
        authSession.signedInUserId.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                local.observe(userId)
                    .map { it?.defaultAreaId }
                    .distinctUntilChanged()
                    .flatMapLatest { areaId -> if (areaId == null) flowOf(null) else areaRepository.observeArea(areaId) }
            }
        }

    // Saves the default area on the device and to the API (FR-AREA-03). The other settings keep their current
    // values: the device copy if there is one, else the API's copy, else the defaults for a first save.
    suspend fun setDefaultArea(areaId: String): SaveSettingsResult {
        val userId = authSession.currentUserId() ?: return SaveSettingsResult.Failed(DataFailure.NOT_SIGNED_IN)

        // user_settings.defaultAreaId is a foreign key into the area catalogue
        if (!areaRepository.ensureAreaCached(areaId)) {
            return SaveSettingsResult.Failed(DataFailure.OFFLINE)
        }

        val now = Instant.now()
        val current = local.get(userId) ?: restoreFromServer(userId) ?: SettingsRules.defaults(userId, now)
        val updated = current.copy(defaultAreaId = areaId, updatedAt = now)

        val invalidFields = SettingsRules.invalidFields(updated)
        if (invalidFields.isNotEmpty()) {
            Log.w(LogTags.API, "Settings not saved; invalid fields: $invalidFields")
            return SaveSettingsResult.Rejected(invalidFields)
        }

        local.save(updated)
        return pushToServer(userId, updated)
    }

    // Sends the device copy to the API again, after SavedOnDeviceOnly
    suspend fun retrySync(): SaveSettingsResult {
        val userId = authSession.currentUserId() ?: return SaveSettingsResult.Failed(DataFailure.NOT_SIGNED_IN)
        val settings = local.get(userId) ?: return SaveSettingsResult.Failed(DataFailure.SERVER)
        return pushToServer(userId, settings)
    }

    // Copies the API's settings into Room, e.g. after signing in on a new device. Returns null when the API
    // cannot be reached.
    suspend fun restoreFromServer(userId: String): UserSettingsEntity? =
        when (val result = remote.getSettings(userId)) {
            is RemoteResult.Success -> cacheServerSettings(result.data)
            else -> null
        }

    private suspend fun pushToServer(userId: String, settings: UserSettingsEntity): SaveSettingsResult =
        when (val result = remote.updateSettings(userId, settings.toUpdateDto())) {
            is RemoteResult.Success -> {
                cacheServerSettings(result.data)
                SaveSettingsResult.Saved
            }
            is RemoteResult.NetworkFailure -> SaveSettingsResult.SavedOnDeviceOnly(DataFailure.OFFLINE)
            is RemoteResult.HttpFailure -> when (result.status) {
                400 -> SaveSettingsResult.Rejected(result.problem?.errors?.keys?.toList().orEmpty())
                401 -> SaveSettingsResult.SavedOnDeviceOnly(DataFailure.NOT_SIGNED_IN)
                else -> SaveSettingsResult.SavedOnDeviceOnly(DataFailure.SERVER)
            }
        }

    private suspend fun cacheServerSettings(dto: UserSettingsDto): UserSettingsEntity {
        // Keep the foreign key valid: drop a default area that cannot be cached rather than fail the save
        val areaAvailable = dto.defaultAreaId?.let { areaRepository.ensureAreaCached(it) } ?: true
        val entity = dto.toEntity().let { if (areaAvailable) it else it.copy(defaultAreaId = null) }
        local.save(entity)
        return entity
    }
}
