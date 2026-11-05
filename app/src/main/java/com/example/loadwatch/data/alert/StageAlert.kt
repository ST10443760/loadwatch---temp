package com.example.loadwatch.data.alert

import java.time.Instant
import java.time.format.DateTimeParseException

// A stage change for one area, as the API sends it in an FCM data message (StageAlertMessage.Data)
data class StageAlert(
    val areaId: String,
    val areaName: String,
    val previousStage: Int,
    val stage: Int,
    val changedAt: Instant,
)

const val STAGE_ALERT_TYPE = "STAGE_CHANGE"

// Null for any other kind of message, or one with a missing or malformed field, so a bad payload is ignored
// rather than crashing the messaging service
fun parseStageAlert(data: Map<String, String>): StageAlert? {
    if (data["type"] != STAGE_ALERT_TYPE) return null
    val areaId = data["areaId"]?.takeIf { it.isNotBlank() } ?: return null
    val stage = data["stage"]?.toIntOrNull()?.takeIf { it in 0..8 } ?: return null
    val previousStage = data["previousStage"]?.toIntOrNull()?.takeIf { it in 0..8 } ?: return null
    val changedAt = try {
        Instant.parse(data["changedAt"] ?: return null)
    } catch (e: DateTimeParseException) {
        return null
    }
    return StageAlert(
        areaId = areaId,
        areaName = data["areaName"]?.takeIf { it.isNotBlank() } ?: areaId,
        previousStage = previousStage,
        stage = stage,
        changedAt = changedAt,
    )
}
