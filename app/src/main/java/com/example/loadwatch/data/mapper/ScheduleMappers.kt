package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.AccuracySummaryEntity
import com.example.loadwatch.core.database.entity.CurrentStageEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.network.dto.AccuracySummaryDto
import com.example.loadwatch.core.network.dto.CurrentStageDto
import com.example.loadwatch.core.network.dto.ScheduleEventDto
import com.example.loadwatch.data.model.ScheduleEvent

/** Maps an outage window from the API to its Room row. */
fun ScheduleEventDto.toEntity() = ScheduleEventEntity(
    scheduleId = scheduleId,
    areaId = areaId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    source = source,
    updatedAt = updatedAt,
)

/** Maps a cached outage window to the domain model. */
fun ScheduleEventEntity.toModel() = ScheduleEvent(
    scheduleId = scheduleId,
    areaId = areaId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    updatedAt = updatedAt,
)

/** Maps an area's accuracy score from the API to its Room row. */
fun AccuracySummaryDto.toEntity() = AccuracySummaryEntity(
    areaId = areaId,
    accuracyScore = accuracyScore,
    sampleSize = sampleSize,
    windowHours = windowHours,
    calculatedAt = calculatedAt,
)

/** Maps the national stage from the API to its single Room row. */
fun CurrentStageDto.toEntity() = CurrentStageEntity(stage = stage, updatedAt = updatedAt)
