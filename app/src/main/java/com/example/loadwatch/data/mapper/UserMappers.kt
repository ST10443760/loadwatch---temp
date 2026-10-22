package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import com.example.loadwatch.core.network.dto.FavouriteAreaDto
import com.example.loadwatch.core.network.dto.UserSettingsDto
import com.example.loadwatch.core.network.dto.UserSettingsUpdateDto

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
