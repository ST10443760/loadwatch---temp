package com.example.loadwatch.data.history

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.data.model.HistoryOutage
import com.example.loadwatch.data.model.HistoryReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class HistoryCalendarTest {

    // 10:00 in Johannesburg on 6 November 2026
    private val clock = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `days are grouped by the South African date, not the phone's time zone`() {
        // 23:30 UTC on 5 November is 01:30 on 6 November in Johannesburg
        val lateOutage = outage("a", "2026-11-05T23:30:00Z", "2026-11-06T02:00:00Z")
        val report = HistoryReport("r", OutageEventType.POWER_OFF, Instant.parse("2026-11-05T21:00:00Z"))

        val days = HistoryCalendar.groupByDay(listOf(lateOutage), listOf(report))

        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)), days.map { it.date })
        assertEquals(listOf(report), days[0].reports)
        assertEquals(listOf(lateOutage), days[1].outages)
    }

    @Test
    fun `only days with an outage or a report are listed, oldest first, each in time order`() {
        val later = outage("later", "2026-11-03T14:00:00Z", "2026-11-03T16:30:00Z")
        val earlier = outage("earlier", "2026-11-03T06:00:00Z", "2026-11-03T08:30:00Z")
        val other = outage("other", "2026-11-01T06:00:00Z", "2026-11-01T08:30:00Z")

        val days = HistoryCalendar.groupByDay(listOf(later, other, earlier), emptyList())

        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), days.map { it.date })
        assertEquals(listOf("earlier", "later"), days[1].outages.map { it.scheduleId })
    }

    @Test
    fun `the month grid starts on Monday with blanks around the month`() {
        // 1 November 2026 is a Sunday and 30 November a Monday
        val weeks = HistoryCalendar.weeks(YearMonth.of(2026, 11))

        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(List(6) { null } + LocalDate.of(2026, 11, 1), weeks.first())
        assertEquals(listOf(LocalDate.of(2026, 11, 30)) + List(6) { null }, weeks.last())
        assertEquals(30, weeks.flatten().count { it != null })
    }

    @Test
    fun `a month that starts on Monday and ends on Sunday has no blanks`() {
        val weeks = HistoryCalendar.weeks(YearMonth.of(2021, 2))

        assertEquals(4, weeks.size)
        assertTrue(weeks.flatten().none { it == null })
    }

    @Test
    fun `time without power counts an ongoing outage only up to now`() {
        val now = Instant.parse("2026-11-06T09:00:00Z")
        val finished = outage("done", "2026-11-06T02:00:00Z", "2026-11-06T04:30:00Z")
        val ongoing = outage("now", "2026-11-06T08:00:00Z", "2026-11-06T10:30:00Z")
        val notStarted = outage("later", "2026-11-06T12:00:00Z", "2026-11-06T14:30:00Z")

        assertEquals(150L + 60L, HistoryCalendar.minutesWithoutPower(listOf(finished, ongoing, notStarted), now))
    }

    @Test
    fun `days are shaded by hours without power`() {
        assertEquals(0, HistoryCalendar.level(0))
        assertEquals(1, HistoryCalendar.level(150))
        assertEquals(2, HistoryCalendar.level(300))
        assertEquals(3, HistoryCalendar.level(360))
    }

    @Test
    fun `months outside the range are pulled back into it`() {
        assertEquals(YearMonth.of(2026, 11), HistoryCalendar.clamp(YearMonth.of(2027, 3), clock))
        assertEquals(YearMonth.of(2025, 11), HistoryCalendar.clamp(YearMonth.of(2020, 1), clock))
        assertEquals(YearMonth.of(2026, 5), HistoryCalendar.clamp(YearMonth.of(2026, 5), clock))
    }

    @Test
    fun `a saved month that cannot be read is ignored`() {
        assertEquals(YearMonth.of(2026, 11), HistoryCalendar.parseMonth("2026-11"))
        assertNull(HistoryCalendar.parseMonth("2026-13"))
        assertNull(HistoryCalendar.parseMonth("november"))
        assertNull(HistoryCalendar.parseMonth(null))
    }

    @Test
    fun `the current month and today are South African`() {
        val justBeforeMidnightUtc = Clock.fixed(Instant.parse("2026-11-30T23:00:00Z"), ZoneOffset.UTC)

        assertEquals(YearMonth.of(2026, 12), HistoryCalendar.currentMonth(justBeforeMidnightUtc))
        assertEquals(LocalDate.of(2026, 12, 1), HistoryCalendar.today(justBeforeMidnightUtc))
    }

    private fun outage(id: String, start: String, end: String) =
        HistoryOutage(id, 2, Instant.parse(start), Instant.parse(end), 0)
}
