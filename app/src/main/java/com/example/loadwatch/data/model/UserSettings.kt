package com.example.loadwatch.data.model

import java.time.Instant

// The user's settings as S09 edits them (FR-SET-01 to 05). Language and theme stay as their stored codes so an
// invalid value can be caught by validation rather than lost in a conversion.
data class UserSettings(
    val defaultAreaId: String?,
    val notificationLeadMinutes: Int,
    // Turning notifications off keeps the stored lead time (FR-SET-03)
    val notificationsEnabled: Boolean,
    val enabledAlertTypes: Set<AlertType>,
    // en, zu or af
    val languageCode: String,
    // system, light or dark
    val themePreference: String,
    val biometricEnabled: Boolean,
    val updatedAt: Instant,
)

/** The kinds of alert a user can tick in S09 Settings. */
enum class AlertType {
    POWER_OFF,
    POWER_BACK_ON,
    STAGE_CHANGE,
}

/** The app's supported languages, with the code that is stored and sent to the API. */
enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    ISIZULU("zu"),
    AFRIKAANS("af"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** The theme choice in S09 Settings, with the code that is stored and sent to the API. */
enum class ThemePreference(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromCode(code: String?): ThemePreference? = entries.firstOrNull { it.code == code }
    }
}
