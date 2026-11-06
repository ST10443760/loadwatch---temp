package com.example.loadwatch.data.remote

import com.example.loadwatch.core.network.LoadWatchApi
import com.example.loadwatch.core.network.dto.DeviceRegistrationDto
import com.example.loadwatch.core.network.dto.NotificationRegistrationDto
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Registers this phone's FCM token with the API so the server can target it. */
class PushRemoteDataSource @Inject constructor(
    private val api: LoadWatchApi,
    private val json: Json,
) {
    suspend fun registerDevice(fcmToken: String): RemoteResult<DeviceRegistrationDto> =
        safeApiCall(json, "registerDevice") { api.registerDevice(NotificationRegistrationDto(fcmToken)) }
}
