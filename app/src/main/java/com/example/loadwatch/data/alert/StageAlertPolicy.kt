package com.example.loadwatch.data.alert

import com.example.loadwatch.data.model.AlertType
import com.example.loadwatch.data.model.ScheduleEvent
import com.example.loadwatch.data.model.UserSettings
import java.time.Duration
import java.time.Instant

/** Whether a received stage-change alert should be shown, and if not, the reason that is logged. */
sealed interface StageAlertDecision {
    // nextOutageMinutes is set when the next outage starts within the user's lead time, so the alert can say so
    data class Show(val nextOutageMinutes: Long?) : StageAlertDecision

    data class Skip(val reason: String) : StageAlertDecision
}

// Whether a stage-change alert should reach the user, following their S09 settings: alerts on, stage changes ticked,
// and the alert for the area they have selected. The lead time decides whether an outage is close enough to mention.
object StageAlertPolicy {

    // An alert this old is no longer news, e.g. one held back while the phone was off for a day
    val MAX_AGE: Duration = Duration.ofHours(1)

    fun decide(alert: StageAlert, settings: UserSettings?, upcoming: List<ScheduleEvent>, now: Instant): StageAlertDecision {
        if (settings == null) return StageAlertDecision.Skip("signed out")
        if (!settings.notificationsEnabled) return StageAlertDecision.Skip("alerts are off")
        if (AlertType.STAGE_CHANGE !in settings.enabledAlertTypes) return StageAlertDecision.Skip("stage change alerts are off")
        if (alert.areaId != settings.defaultAreaId) return StageAlertDecision.Skip("not the selected area")
        if (Duration.between(alert.changedAt, now) > MAX_AGE) return StageAlertDecision.Skip("older than $MAX_AGE")

        val lead = Duration.ofMinutes(settings.notificationLeadMinutes.toLong())
        val nextOutageMinutes = upcoming
            .filter { it.areaId == alert.areaId && it.startAt.isAfter(now) }
            .minByOrNull { it.startAt }
            ?.let { Duration.between(now, it.startAt) }
            ?.takeIf { it <= lead }
            // Rounded up, so "in 0 minutes" is never shown for an outage starting within the minute
            ?.let { (it.seconds + 59) / 60 }
        // With load shedding over there is no outage to warn about
        return StageAlertDecision.Show(nextOutageMinutes.takeIf { alert.stage > 0 })
    }
}
