package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.data.local.SettingsLocalDataSource
import com.example.loadwatch.data.local.SettingsRules
import com.example.loadwatch.data.mapper.toEntity
import com.example.loadwatch.data.mapper.toModel
import com.example.loadwatch.data.mapper.toUpdateDto
import com.example.loadwatch.data.model.AppLanguage
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.ThemePreference
import com.example.loadwatch.data.model.UserSettings
import com.example.loadwatch.data.remote.RemoteResult
import com.example.loadwatch.data.remote.SettingsRemoteDataSource
import com.example.loadwatch.data.sync.SyncEntityType
import com.example.loadwatch.data.sync.SyncQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outcome of saving settings: saved everywhere, saved only on the phone, rejected by the API's rules, or
 * not saved.
 */
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

/**
 * The signed-in user's settings, offline first. Each change is saved in Room, then sent to the API, or queued
 * for the sync worker when offline. Server copies never overwrite a newer edit made on the phone.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val authSession: AuthSession,
    private val local: SettingsLocalDataSource,
    private val remote: SettingsRemoteDataSource,
    private val areaRepository: AreaRepository,
    private val syncQueue: SyncQueue,
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

    // The signed-in user's settings from Room, so S09 opens instantly and offline
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSettings(): Flow<UserSettings?> =
        authSession.signedInUserId.flatMapLatest { userId ->
            if (userId == null) flowOf(null) else local.observe(userId).map { it?.toModel() }
        }

    // The stored theme for the whole app, or null before any settings exist
    fun observeThemePreference(): Flow<ThemePreference?> =
        observeSettings().map { ThemePreference.fromCode(it?.themePreference) }.distinctUntilChanged()

    // The stored app language, or null before any settings exist, when the phone's language is left alone
    fun observeLanguage(): Flow<AppLanguage?> =
        observeSettings().map { AppLanguage.fromCode(it?.languageCode) }.distinctUntilChanged()

    fun currentUserEmail(): String? = authSession.currentUserEmail()

    // Pulls the API's copy into Room, which observeSettings then emits. Returns the failure, or null on success.
    suspend fun refreshFromServer(): DataFailure? {
        val userId = authSession.currentUserId() ?: return DataFailure.NOT_SIGNED_IN
        return if (restoreFromServer(userId) != null) null else DataFailure.OFFLINE
    }

    // Validates, then saves on the device and to the API. Nothing is stored or sent if any field breaks the
    // API's rules (FR-SET-05).
    suspend fun save(settings: UserSettings): SaveSettingsResult {
        val userId = authSession.currentUserId() ?: return SaveSettingsResult.Failed(DataFailure.NOT_SIGNED_IN)
        val entity = settings.copy(updatedAt = Instant.now()).toEntity(userId)

        val invalidFields = SettingsRules.invalidFields(entity)
        if (invalidFields.isNotEmpty()) {
            Log.w(LogTags.API, "Settings not saved; invalid fields: $invalidFields")
            return SaveSettingsResult.Rejected(invalidFields)
        }
        val defaultAreaId = entity.defaultAreaId
        if (defaultAreaId != null && !areaRepository.ensureAreaCached(defaultAreaId)) {
            return SaveSettingsResult.Failed(DataFailure.OFFLINE)
        }

        local.save(entity)
        Log.i(LogTags.API, "Settings saved on the device; sending to the API")
        return pushToServer(userId, entity)
    }

    // Sends the device copy to the API again, after SavedOnDeviceOnly
    suspend fun retrySync(): SaveSettingsResult {
        val userId = authSession.currentUserId() ?: return SaveSettingsResult.Failed(DataFailure.NOT_SIGNED_IN)
        val settings = local.get(userId) ?: return SaveSettingsResult.Failed(DataFailure.SERVER)
        return pushToServer(userId, settings)
    }

    // Copies the API's settings into Room, e.g. after signing in on a new device. Returns null when the API
    // cannot be reached. Settings saved offline and still queued are newer than the API's copy, so they are kept.
    suspend fun restoreFromServer(userId: String): UserSettingsEntity? {
        if (syncQueue.hasUnsent(SyncEntityType.SETTINGS)) {
            Log.i(LogTags.SYNC, "Kept the settings saved on this phone; they are still waiting to sync")
            return local.get(userId)
        }
        return when (val result = remote.getSettings(userId)) {
            is RemoteResult.Success -> applyServerSettings(result.data)
            else -> null
        }
    }

    // A save that cannot reach the API is queued, and the sync worker sends it once the phone is online. The time it
    // was saved goes with it, so the API keeps whichever copy was written last.
    private suspend fun pushToServer(userId: String, settings: UserSettingsEntity): SaveSettingsResult =
        when (val result = remote.updateSettings(userId, settings.toUpdateDto())) {
            is RemoteResult.Success -> {
                applyServerSettings(result.data)
                // Anything queued earlier is older than what the API now has
                syncQueue.remove(SyncEntityType.SETTINGS, userId)
                Log.i(LogTags.SYNC, "Settings synced to the API")
                SaveSettingsResult.Saved
            }
            is RemoteResult.NetworkFailure -> {
                syncQueue.enqueueSettings(userId, settings.toUpdateDto(), settings.updatedAt)
                Log.i(LogTags.SYNC, "Settings kept on the device and queued: offline")
                SaveSettingsResult.SavedOnDeviceOnly(DataFailure.OFFLINE)
            }
            is RemoteResult.HttpFailure -> when (result.status) {
                400 -> SaveSettingsResult.Rejected(result.problem?.errors?.keys?.toList().orEmpty())
                401 -> SaveSettingsResult.SavedOnDeviceOnly(DataFailure.NOT_SIGNED_IN)
                else -> SaveSettingsResult.SavedOnDeviceOnly(DataFailure.SERVER)
            }.also {
                if (it is SaveSettingsResult.SavedOnDeviceOnly) syncQueue.enqueueSettings(userId, settings.toUpdateDto(), settings.updatedAt)
                Log.w(LogTags.SYNC, "Settings sync answered HTTP ${result.status}: ${it.javaClass.simpleName}")
            }
        }

    // Stores the API's copy in Room. Also used by the sync worker, whose answer carries the API's settings.
    suspend fun applyServerSettings(dto: UserSettingsDto): UserSettingsEntity {
        // Keep the foreign key valid: drop a default area that cannot be cached rather than fail the save
        val areaAvailable = dto.defaultAreaId?.let { areaRepository.ensureAreaCached(it) } ?: true
        val entity = dto.toEntity().let { if (areaAvailable) it else it.copy(defaultAreaId = null) }
        local.save(entity)
        Log.d(LogTags.ROOM, "Cached the API's settings (language ${entity.languageCode}, theme ${entity.themePreference})")
        return entity
    }
}
