package com.example.loadwatch.data.history

import com.example.loadwatch.data.model.HistoryDay
import com.example.loadwatch.data.model.HistoryOutage
import com.example.loadwatch.data.model.HistoryReport
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// The calendar arithmetic behind S10 Outage History. Days are South African dates, as the API groups them, whatever
// time zone the phone is set to.
object HistoryCalendar {

    val ZONE: ZoneId = ZoneId.of("Africa/Johannesburg")

    // How far back the month switcher goes
    const val MAX_MONTHS_BACK = 12L

    fun currentMonth(clock: Clock): YearMonth = YearMonth.from(clock.instant().atZone(ZONE))

    fun today(clock: Clock): LocalDate = LocalDate.ofInstant(clock.instant(), ZONE)

    fun earliestMonth(clock: Clock): YearMonth = currentMonth(clock).minusMonths(MAX_MONTHS_BACK)

    // Keeps a month restored from saved state, or reached by tapping, inside the range the API can answer
    fun clamp(month: YearMonth, clock: Clock): YearMonth {
        val latest = currentMonth(clock)
        val earliest = earliestMonth(clock)
        return when {
            month.isAfter(latest) -> latest
            month.isBefore(earliest) -> earliest
            else -> month
        }
    }

    // Parses "2026-11", or null for anything else
    fun parseMonth(text: String?): YearMonth? = text?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    fun dateOf(instant: Instant): LocalDate = LocalDate.ofInstant(instant, ZONE)

    // Only days with an outage or a report, oldest first
    fun groupByDay(outages: List<HistoryOutage>, reports: List<HistoryReport>): List<HistoryDay> {
        val outagesByDay = outages.sortedBy { it.startAt }.groupBy { dateOf(it.startAt) }
        val reportsByDay = reports.sortedBy { it.reportedAt }.groupBy { dateOf(it.reportedAt) }
        return (outagesByDay.keys + reportsByDay.keys).sorted().map { date ->
            HistoryDay(date, outagesByDay[date].orEmpty(), reportsByDay[date].orEmpty())
        }
    }

    // The month as weeks of seven, starting on Monday, with nulls before the 1st and after the last day
    fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<List<LocalDate?>> {
        val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDayOfWeek.plus(6)))
        return generateSequence(gridStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(gridEnd) }
            .map { date -> date.takeIf { YearMonth.from(it) == month } }
            .chunked(7)
            .toList()
    }

    // Time without power from outages that have started, counting an ongoing one only up to now
    fun minutesWithoutPower(outages: List<HistoryOutage>, now: Instant): Long =
        outages.sumOf { outage ->
            val end = if (outage.endAt.isAfter(now)) now else outage.endAt
            Duration.between(outage.startAt, end).toMinutes().coerceAtLeast(0)
        }

    // How dark a day is shaded: 0 for no outage, then up to 3 for six hours or more without power
    fun level(minutesWithoutPower: Long): Int = when {
        minutesWithoutPower <= 0 -> 0
        minutesWithoutPower < 180 -> 1
        minutesWithoutPower < 360 -> 2
        else -> 3
    }
}
