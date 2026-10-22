package com.example.loadwatch.data.schedule

import com.example.loadwatch.data.model.ScheduleEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduleDay(
    val date: LocalDate,
    // Windows overlapping this day, earliest first. Empty means no scheduled outage, if the day is loaded.
    val events: List<ScheduleEvent>,
    // False when the cached schedule does not reach this day yet, so an empty day is unknown rather than clear
    val isLoaded: Boolean,
)

// Groups schedule windows into calendar days in the device's time zone for the S06 timeline (FR-SCHED-01, 02)
object ScheduleDays {

    // One entry per day from firstDay for dayCount days, including days with no windows. A window that crosses
    // midnight appears on both days it touches. loadedUntil is the end of the last fetched schedule window.
    fun group(
        events: List<ScheduleEvent>,
        firstDay: LocalDate,
        dayCount: Int,
        zone: ZoneId,
        loadedUntil: Instant?,
    ): List<ScheduleDay> {
        val sorted = events.sortedBy { it.startAt }
        return (0 until dayCount).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            ScheduleDay(
                date = date,
                events = sorted.filter { it.startAt.isBefore(dayEnd) && it.endAt.isAfter(dayStart) },
                isLoaded = loadedUntil != null && dayStart.isBefore(loadedUntil),
            )
        }
    }
}
