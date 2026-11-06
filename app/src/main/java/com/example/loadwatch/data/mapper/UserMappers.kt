package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.FavouriteWithArea
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto
import com.example.loadwatch.data.model.AlertType
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.model.UserSettings

/** Maps the API's copy of the user's settings to the Room row. */
fun UserSettingsDto.toEntity() = UserSettingsEntity(
    userId = userId,
    defaultAreaId = defaultAreaId,
    notificationLeadMinutes = notificationLeadMinutes,
    notificationsEnabled = notificationsEnabled,
    enabledAlertTypes = enabledAlertTypes,
    languageCode = languageCode,
    themePreference = themePreference,
    biometricEnabled = biometricEnabled,
    updatedAt = updatedAt,
)

/** Builds the PUT body that sends the stored settings to the API. */
fun UserSettingsEntity.toUpdateDto() = UserSettingsUpdateDto(
    defaultAreaId = defaultAreaId,
    notificationLeadMinutes = notificationLeadMinutes,
    notificationsEnabled = notificationsEnabled,
    enabledAlertTypes = enabledAlertTypes,
    languageCode = languageCode,
    themePreference = themePreference,
    biometricEnabled = biometricEnabled,
)

/** Maps a favourite from the API to its Room row. */
fun FavouriteAreaDto.toEntity() = FavouriteAreaEntity(
    favouriteId = favouriteId,
    userId = userId,
    areaId = areaId,
    label = label,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

/** Maps a favourite joined with its area to the domain model S08 Compare shows. */
fun FavouriteWithArea.toModel() = Favourite(
    favouriteId = favourite.favouriteId,
    area = area.toModel(),
    label = favourite.label,
    sortOrder = favourite.sortOrder,
)

/** Maps the stored settings row to the domain model. */
fun UserSettingsEntity.toModel() = UserSettings(
    defaultAreaId = defaultAreaId,
    notificationLeadMinutes = notificationLeadMinutes,
    notificationsEnabled = notificationsEnabled,
    // Unknown alert types from a newer API are dropped rather than failing the whole screen
    enabledAlertTypes = enabledAlertTypes.split(',')
        .mapNotNull { code -> AlertType.entries.firstOrNull { it.name == code.trim() } }
        .toSet(),
    languageCode = languageCode,
    themePreference = themePreference,
    biometricEnabled = biometricEnabled,
    updatedAt = updatedAt,
)

/** Maps edited settings back to the Room row for the given user. */
fun UserSettings.toEntity(userId: String) = UserSettingsEntity(
    userId = userId,
    defaultAreaId = defaultAreaId,
    notificationLeadMinutes = notificationLeadMinutes,
    notificationsEnabled = notificationsEnabled,
    // Stored in a fixed order so the same choice always produces the same string
    enabledAlertTypes = AlertType.entries.filter { it in enabledAlertTypes }.joinToString(",") { it.name },
    languageCode = languageCode,
    themePreference = themePreference,
    biometricEnabled = biometricEnabled,
    updatedAt = updatedAt,
)
