package com.example.loadwatch.data.schedule

import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.data.model.ScheduleEvent
import java.time.Duration
import java.time.Instant

data class Countdown(
    // OFF inside a scheduled window, ON outside one
    val state: SupplyState,
    // The corrected current instant the countdown was calculated for
    val now: Instant,
    // When the state next changes: the window's end when OFF (power returns), the next window's start when ON
    // (power off). Null when ON with no upcoming window.
    val target: Instant?,
    val remaining: Duration?,
    // The window in progress, when OFF
    val currentEvent: ScheduleEvent?,
    // The next window that has not started yet (after the current block when OFF)
    val nextEvent: ScheduleEvent?,
)

// Works out supply state and the countdown from cached schedule windows, with no Android or network
// dependencies, so it runs every tick offline (FR-COUNT-01 to 03).
//
// Boundaries match the API: a window is [startAt, endAt), so the start instant is OFF and the end instant is ON.
// Back-to-back or overlapping windows count as one block, so "power returns" points at when it really returns.
class CountdownCalculator {

    // clockOffset is serverTime minus the device clock, so deviceNow + clockOffset is the server's idea of now.
    // That keeps the countdown right on a phone whose clock is wrong.
    fun calculate(events: List<ScheduleEvent>, deviceNow: Instant, clockOffset: Duration = Duration.ZERO): Countdown {
        val now = deviceNow.plus(clockOffset)
        val windows = events.filter { it.endAt.isAfter(it.startAt) }.sortedBy { it.startAt }

        val current = windows.firstOrNull { !now.isBefore(it.startAt) && now.isBefore(it.endAt) }
        if (current != null) {
            var blockEnd = current.endAt
            windows.forEach { window ->
                if (!window.startAt.isAfter(blockEnd) && window.endAt.isAfter(blockEnd)) {
                    blockEnd = window.endAt
                }
            }
            return Countdown(
                state = SupplyState.OFF,
                now = now,
                target = blockEnd,
                remaining = Duration.between(now, blockEnd),
                currentEvent = current,
                nextEvent = windows.firstOrNull { it.startAt.isAfter(blockEnd) },
            )
        }

        val next = windows.firstOrNull { it.startAt.isAfter(now) }
        return Countdown(
            state = SupplyState.ON,
            now = now,
            target = next?.startAt,
            remaining = next?.let { Duration.between(now, it.startAt) },
            currentEvent = null,
            nextEvent = next,
        )
    }
}
