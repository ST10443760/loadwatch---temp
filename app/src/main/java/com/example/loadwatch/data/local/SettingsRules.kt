package com.example.loadwatch.data.local

import com.example.loadwatch.core.database.entity.UserSettingsEntity
import java.time.Instant

// The API's rules for user settings, checked on the device before anything is saved or sent (FR-SET-05),
// so an invalid value never reaches the API.
object SettingsRules {
    const val MIN_LEAD_MINUTES = 5
    const val MAX_LEAD_MINUTES = 120
    val LANGUAGES = setOf("en", "zu", "af")
    val THEMES = setOf("system", "light", "dark")
    val ALERT_TYPES = listOf("POWER_OFF", "POWER_BACK_ON", "STAGE_CHANGE")

    // Used for the first save when neither the device nor the API has settings for this user
    fun defaults(userId: String, now: Instant) = UserSettingsEntity(
        userId = userId,
        defaultAreaId = null,
        notificationLeadMinutes = 30,
        notificationsEnabled = true,
        enabledAlertTypes = ALERT_TYPES.joinToString(","),
        languageCode = "en",
        themePreference = "system",
        biometricEnabled = false,
        updatedAt = now,
    )

    // The JSON names of any fields that break the rules; empty when the settings are valid
    fun invalidFields(settings: UserSettingsEntity): List<String> = buildList {
        if (settings.notificationLeadMinutes !in MIN_LEAD_MINUTES..MAX_LEAD_MINUTES) add("notificationLeadMinutes")
        if (settings.languageCode !in LANGUAGES) add("languageCode")
        if (settings.themePreference !in THEMES) add("themePreference")
        val alertTypes = settings.enabledAlertTypes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (!ALERT_TYPES.containsAll(alertTypes)) add("enabledAlertTypes")
    }
}
