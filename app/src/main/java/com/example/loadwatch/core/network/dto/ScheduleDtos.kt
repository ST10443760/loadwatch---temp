package com.example.loadwatch.core.network.dto

import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.core.network.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

// GET /api/schedules?areaId=&from=&to= returns ScheduleEvent[]
@Serializable
data class ScheduleEventDto(
    val scheduleId: String,
    val areaId: String,
    val stage: Int,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant,
    val source: String,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

// GET /api/schedules/next?areaId= returns NextEvent (Table 7).
// When currentState is OFF the window is the current event, so the countdown targets nextEndAt;
// when ON it is the next event, so the countdown targets nextStartAt. serverTime corrects for device clock drift.
@Serializable
data class NextEventDto(
    val areaId: String,
    val areaName: String,
    val currentState: SupplyState,
    val stage: Int,
    @Serializable(with = InstantSerializer::class) val nextStartAt: Instant,
    @Serializable(with = InstantSerializer::class) val nextEndAt: Instant,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

// GET /api/stages/current returns CurrentStage
@Serializable
data class CurrentStageDto(
    val stage: Int,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)
