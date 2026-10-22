package com.example.loadwatch.data.schedule

import java.time.Duration
import java.time.Instant

// FR-SCHED-04: cached schedule data older than six hours is flagged as stale
object ScheduleFreshness {
    val STALE_AFTER: Duration = Duration.ofHours(6)

    // Stale once strictly older than staleAfter; data that was never refreshed is always stale
    fun isStale(lastRefreshedAt: Instant?, now: Instant, staleAfter: Duration = STALE_AFTER): Boolean =
        lastRefreshedAt == null || Duration.between(lastRefreshedAt, now) > staleAfter
}
