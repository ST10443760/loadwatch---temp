package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Reads and updates the signed-in user's settings on the API. */
class SettingsRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun getSettings(userId: String): RemoteResult<UserSettingsDto> =
        safeApiCall(json, "getSettings") { api.getSettings(userId) }

    suspend fun updateSettings(userId: String, update: UserSettingsUpdateDto): RemoteResult<UserSettingsDto> =
        safeApiCall(json, "updateSettings") { api.updateSettings(userId, update) }
}
