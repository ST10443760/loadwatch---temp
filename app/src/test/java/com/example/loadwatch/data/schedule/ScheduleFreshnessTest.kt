package com.example.loadwatch.data.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ScheduleFreshnessTest {

    private val refreshedAt = Instant.parse("2026-10-22T06:00:00Z")

    @Test
    fun `the threshold is six hours`() {
        assertEquals(Duration.ofHours(6), ScheduleFreshness.STALE_AFTER)
    }

    @Test
    fun `data just under six hours old is fresh`() {
        assertFalse(ScheduleFreshness.isStale(refreshedAt, refreshedAt.plus(Duration.parse("PT5H59M59S"))))
    }

    @Test
    fun `data exactly six hours old is still fresh`() {
        assertFalse(ScheduleFreshness.isStale(refreshedAt, refreshedAt.plus(Duration.ofHours(6))))
    }

    @Test
    fun `data a second past six hours is stale`() {
        assertTrue(ScheduleFreshness.isStale(refreshedAt, refreshedAt.plus(Duration.ofHours(6)).plusSeconds(1)))
    }

    @Test
    fun `data that was never refreshed is stale`() {
        assertTrue(ScheduleFreshness.isStale(null, refreshedAt))
    }

    @Test
    fun `the threshold can be overridden`() {
        val now = refreshedAt.plus(Duration.ofMinutes(31))

        assertTrue(ScheduleFreshness.isStale(refreshedAt, now, staleAfter = Duration.ofMinutes(30)))
        assertFalse(ScheduleFreshness.isStale(refreshedAt, now))
    }
}
