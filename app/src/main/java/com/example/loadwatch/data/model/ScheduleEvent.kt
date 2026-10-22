package com.example.loadwatch.data.model

import java.time.Instant

// One scheduled interruption window for an area, as the features and the countdown see it
data class ScheduleEvent(
    val scheduleId: String,
    val areaId: String,
    val stage: Int,
    val startAt: Instant,
    val endAt: Instant,
    val updatedAt: Instant,
)
