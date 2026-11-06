package com.example.loadwatch.core.network.dto

import com.example.loadwatch.core.network.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

// POST /api/sync body: everything queued on the phone while it was offline. Reports are keyed by clientReportId,
// favourite and settings changes by changeId, which is the pending_sync row's syncId.
@Serializable
data class SyncBatchDto(
    val reports: List<OutageReportCreateDto> = emptyList(),
    val favourites: List<FavouriteChangeDto> = emptyList(),
    val settings: SettingsChangeDto? = null,
)

// A favourite saved, relabelled or removed while offline. Saving an existing favourite sets its label.
@Serializable
data class FavouriteChangeDto(
    val changeId: String,
    val areaId: String,
    val label: String? = null,
    val removed: Boolean,
    @Serializable(with = InstantSerializer::class) val changedAt: Instant,
)

// The whole settings form as saved on the phone. The API keeps whichever copy was written last.
@Serializable
data class SettingsChangeDto(
    val changeId: String,
    @Serializable(with = InstantSerializer::class) val changedAt: Instant,
    val defaultAreaId: String?,
    val notificationLeadMinutes: Int,
    val notificationsEnabled: Boolean,
    val enabledAlertTypes: String,
    val languageCode: String,
    val themePreference: String,
    val biometricEnabled: Boolean,
)

// The outcome of each item by the id it was sent with. settings and favourites are the server's copies after the
// batch, sent back when the batch had any.
@Serializable
data class SyncResultDto(
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val superseded: List<String> = emptyList(),
    val rejected: List<SyncRejectionDto> = emptyList(),
    val settings: UserSettingsDto? = null,
    val favourites: List<FavouriteAreaDto>? = null,
    @Serializable(with = InstantSerializer::class) val syncedAt: Instant,
)

/** A queued item the API refused, with its ID and the reason, so the phone can stop retrying it. */
@Serializable
data class SyncRejectionDto(
    val id: String,
    val reason: String,
)
