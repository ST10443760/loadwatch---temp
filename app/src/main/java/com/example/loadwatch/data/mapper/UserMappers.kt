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

fun UserSettingsEntity.toUpdateDto() = UserSettingsUpdateDto(
    defaultAreaId = defaultAreaId,
    notificationLeadMinutes = notificationLeadMinutes,
    notificationsEnabled = notificationsEnabled,
    enabledAlertTypes = enabledAlertTypes,
    languageCode = languageCode,
    themePreference = themePreference,
    biometricEnabled = biometricEnabled,
)

fun FavouriteAreaDto.toEntity() = FavouriteAreaEntity(
    favouriteId = favouriteId,
    userId = userId,
    areaId = areaId,
    label = label,
    sortOrder = sortOrder,
    createdAt = createdAt,
)

fun FavouriteWithArea.toModel() = Favourite(
    favouriteId = favourite.favouriteId,
    area = area.toModel(),
    label = favourite.label,
    sortOrder = favourite.sortOrder,
)

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
