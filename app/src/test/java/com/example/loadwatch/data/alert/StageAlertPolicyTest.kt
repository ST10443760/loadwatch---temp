package com.example.loadwatch.data.alert

import com.example.loadwatch.data.model.AlertType
import com.example.loadwatch.data.model.ScheduleEvent
import com.example.loadwatch.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

// A stage alert follows the S09 settings: the alert switch, the stage change alert type, the selected area and the
// lead time
class StageAlertPolicyTest {

    private val now = Instant.parse("2026-11-05T10:00:00Z")
    private val alert = StageAlert("eth-berea", "Berea", previousStage = 2, stage = 4, changedAt = now.minusSeconds(5))
    private val settings = UserSettings(
        defaultAreaId = "eth-berea",
        notificationLeadMinutes = 30,
        notificationsEnabled = true,
        enabledAlertTypes = AlertType.entries.toSet(),
        languageCode = "en",
        themePreference = "system",
        biometricEnabled = false,
        updatedAt = now,
    )

    @Test
    fun `an alert for the selected area is shown when stage alerts are on`() {
        assertEquals(StageAlertDecision.Show(null), StageAlertPolicy.decide(alert, settings, emptyList(), now))
    }

    @Test
    fun `nothing is shown when alerts are off, even though the lead time is kept`() {
        val decision = StageAlertPolicy.decide(alert, settings.copy(notificationsEnabled = false), emptyList(), now)

        assertTrue(decision is StageAlertDecision.Skip)
    }

    @Test
    fun `nothing is shown when stage change alerts are unticked`() {
        val onlyOutages = settings.copy(enabledAlertTypes = setOf(AlertType.POWER_OFF, AlertType.POWER_BACK_ON))

        assertTrue(StageAlertPolicy.decide(alert, onlyOutages, emptyList(), now) is StageAlertDecision.Skip)
    }

    @Test
    fun `nothing is shown when signed out or for another area`() {
        assertTrue(StageAlertPolicy.decide(alert, null, emptyList(), now) is StageAlertDecision.Skip)
        assertTrue(StageAlertPolicy.decide(alert.copy(areaId = "eth-kloof"), settings, emptyList(), now) is StageAlertDecision.Skip)
    }

    @Test
    fun `an alert delayed by more than an hour is dropped`() {
        val stale = alert.copy(changedAt = now.minus(Duration.ofMinutes(61)))

        assertTrue(StageAlertPolicy.decide(stale, settings, emptyList(), now) is StageAlertDecision.Skip)
    }

    @Test
    fun `an outage starting within the lead time is mentioned in minutes, rounded up`() {
        val soon = outage(startsIn = Duration.ofMinutes(20).plusSeconds(10))

        assertEquals(StageAlertDecision.Show(21), StageAlertPolicy.decide(alert, settings, listOf(soon), now))
    }

    @Test
    fun `an outage beyond the lead time, or already started, is not mentioned`() {
        val later = outage(startsIn = Duration.ofMinutes(45))
        val started = outage(startsIn = Duration.ofMinutes(-10))

        assertEquals(StageAlertDecision.Show(null), StageAlertPolicy.decide(alert, settings, listOf(later, started), now))
    }

    @Test
    fun `when load shedding ends no outage is mentioned`() {
        val ended = alert.copy(stage = 0)

        assertEquals(StageAlertDecision.Show(null), StageAlertPolicy.decide(ended, settings, listOf(outage(Duration.ofMinutes(5))), now))
    }

    private fun outage(startsIn: Duration) = ScheduleEvent(
        scheduleId = "s-$startsIn",
        areaId = "eth-berea",
        stage = 4,
        startAt = now.plus(startsIn),
        endAt = now.plus(startsIn).plus(Duration.ofHours(2)),
        updatedAt = now,
    )
}
