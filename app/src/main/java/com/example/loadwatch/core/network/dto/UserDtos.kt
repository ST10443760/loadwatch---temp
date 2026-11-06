package com.example.loadwatch.core.network.dto

import com.example.loadwatch.core.network.InstantSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.time.Instant

// GET and PUT /api/users/{userId}/settings return UserSettings
@Serializable
data class UserSettingsDto(
    val userId: String,
    val defaultAreaId: String? = null,
    val notificationLeadMinutes: Int,
    val notificationsEnabled: Boolean,
    val enabledAlertTypes: String,
    val languageCode: String,
    val themePreference: String,
    val biometricEnabled: Boolean,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

// PUT /api/users/{userId}/settings body (UserSettingsUpdate)
@Serializable
data class UserSettingsUpdateDto(
    val defaultAreaId: String?,
    val notificationLeadMinutes: Int,
    val notificationsEnabled: Boolean,
    val enabledAlertTypes: String,
    val languageCode: String,
    val themePreference: String,
    val biometricEnabled: Boolean,
)

// GET /api/users/{userId}/favourites returns FavouriteArea[]; POST returns 201 FavouriteArea
@Serializable
data class FavouriteAreaDto(
    val favouriteId: String,
    val userId: String,
    val areaId: String,
    val label: String,
    val sortOrder: Int,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

// POST /api/users/{userId}/favourites body: { areaId, label }
@Serializable
data class FavouriteAreaCreateDto(
    val areaId: String,
    val label: String? = null,
)

// POST /api/notifications/registrations body: the phone's FCM token. The API rejects a body without platform,
// and the shared Json leaves out fields equal to their default, so platform is written out every time.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationRegistrationDto(
    val fcmToken: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val platform: String = "android",
)

// POST /api/notifications/registrations returns the stored DeviceToken (201 new, 200 already registered)
@Serializable
data class DeviceRegistrationDto(
    val token: String,
    val userId: String,
    val platform: String,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)
