package com.example.loadwatch.data.schedule

import com.example.loadwatch.data.model.ScheduleEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ScheduleDaysTest {

    // South Africa Standard Time, UTC+2
    private val zone = ZoneId.of("Africa/Johannesburg")
    private val today = LocalDate.of(2026, 10, 22)
    private val loadedUntil = Instant.parse("2026-10-28T22:00:00Z") // midnight SAST at the end of 28 October

    @Test
    fun `every day in the range is present, including days with no outage`() {
        val days = ScheduleDays.group(emptyList(), today, dayCount = 7, zone = zone, loadedUntil = loadedUntil)

        assertEquals((0L until 7L).map { today.plusDays(it) }, days.map { it.date })
        assertTrue(days.all { it.events.isEmpty() && it.isLoaded })
    }

    @Test
    fun `windows are grouped by local date, not UTC date`() {
        // 23:30 UTC on the 22nd is 01:30 SAST on the 23rd
        val earlyMorning = window("early", "2026-10-22T23:30:00Z", "2026-10-23T02:00:00Z")
        val evening = window("evening", "2026-10-22T18:00:00Z", "2026-10-22T20:30:00Z")

        val days = ScheduleDays.group(listOf(earlyMorning, evening), today, dayCount = 3, zone = zone, loadedUntil = loadedUntil)

        assertEquals(listOf(evening), days[0].events)
        assertEquals(listOf(earlyMorning), days[1].events)
        assertTrue(days[2].events.isEmpty())
    }

    @Test
    fun `a window that crosses midnight appears on both days`() {
        // 23:00 to 01:30 SAST
        val overnight = window("overnight", "2026-10-22T21:00:00Z", "2026-10-22T23:30:00Z")

        val days = ScheduleDays.group(listOf(overnight), today, dayCount = 2, zone = zone, loadedUntil = loadedUntil)

        assertEquals(listOf(overnight), days[0].events)
        assertEquals(listOf(overnight), days[1].events)
    }

    @Test
    fun `windows within a day are in start order`() {
        val late = window("late", "2026-10-22T14:00:00Z", "2026-10-22T16:30:00Z")
        val early = window("early", "2026-10-22T06:00:00Z", "2026-10-22T08:30:00Z")

        val days = ScheduleDays.group(listOf(late, early), today, dayCount = 1, zone = zone, loadedUntil = loadedUntil)

        assertEquals(listOf(early, late), days[0].events)
    }

    @Test
    fun `days past the cached window are not loaded rather than clear`() {
        val days = ScheduleDays.group(emptyList(), today, dayCount = 14, zone = zone, loadedUntil = loadedUntil)

        assertTrue(days.take(7).all { it.isLoaded })
        assertTrue(days.drop(7).none { it.isLoaded })
    }

    @Test
    fun `nothing is loaded before the first refresh`() {
        val days = ScheduleDays.group(emptyList(), today, dayCount = 3, zone = zone, loadedUntil = null)

        assertFalse(days.any { it.isLoaded })
    }

    private fun window(id: String, start: String, end: String) =
        ScheduleEvent(id, "eth-berea", 2, Instant.parse(start), Instant.parse(end), Instant.parse("2026-10-22T06:00:00Z"))
}
