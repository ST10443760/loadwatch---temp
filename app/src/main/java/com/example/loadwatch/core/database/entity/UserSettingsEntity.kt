package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// One row per user, keyed by the Firebase UID. notificationLeadMinutes is 5 to 120 and languageCode is
// en, zu or af. enabledAlertTypes and themePreference carry the alert types and theme from section 3.1.
@Entity(
    tableName = "user_settings",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["defaultAreaId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["defaultAreaId"])],
)
data class UserSettingsEntity(
    @PrimaryKey val userId: String,
    val defaultAreaId: String?,
    val notificationLeadMinutes: Int,
    val notificationsEnabled: Boolean,
    // Comma-separated, e.g. "POWER_OFF,POWER_BACK_ON,STAGE_CHANGE"
    val enabledAlertTypes: String,
    val languageCode: String,
    // system, light or dark
    val themePreference: String,
    val biometricEnabled: Boolean,
    val updatedAt: Instant,
)
