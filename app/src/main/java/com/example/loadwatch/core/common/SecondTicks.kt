package com.example.loadwatch.core.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant

// The device time once a second, aligned to whole seconds so the countdown changes with the system clock.
// Purely local: the countdown runs from the cached schedule with no network (FR-COUNT-01). The flow is cold
// and only ticks while collected, so it stops when the screen using it is not visible.
fun secondTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        val now = clock.instant()
        emit(now)
        delay(MILLIS_PER_SECOND - now.toEpochMilli() % MILLIS_PER_SECOND)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
