package com.example.loadwatch.feature.settings

import com.example.loadwatch.data.local.SettingsRules

sealed interface LeadTimeInput {
    data class Valid(val minutes: Int) : LeadTimeInput
    data object Empty : LeadTimeInput
    data object NotWholeNumber : LeadTimeInput
    data object OutOfRange : LeadTimeInput
}

// Checks what the user typed for the notification lead time before anything is saved or sent (FR-SET-02, 05)
object SettingsFormValidation {

    fun parseLeadMinutes(text: String): LeadTimeInput {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return LeadTimeInput.Empty
        val minutes = trimmed.toIntOrNull() ?: return LeadTimeInput.NotWholeNumber
        return if (minutes in SettingsRules.MIN_LEAD_MINUTES..SettingsRules.MAX_LEAD_MINUTES) {
            LeadTimeInput.Valid(minutes)
        } else {
            LeadTimeInput.OutOfRange
        }
    }
}
