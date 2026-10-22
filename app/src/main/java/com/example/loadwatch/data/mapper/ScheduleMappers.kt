package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.AccuracySummaryEntity
import com.example.loadwatch.core.database.entity.CurrentStageEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.network.dto.AccuracySummaryDto
import com.example.loadwatch.core.network.dto.CurrentStageDto
import com.example.loadwatch.core.network.dto.ScheduleEventDto
import com.example.loadwatch.data.model.ScheduleEvent

fun ScheduleEventDto.toEntity() = ScheduleEventEntity(
    scheduleId = scheduleId,
    areaId = areaId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    source = source,
    updatedAt = updatedAt,
)

fun ScheduleEventEntity.toModel() = ScheduleEvent(
    scheduleId = scheduleId,
    areaId = areaId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    updatedAt = updatedAt,
)

fun AccuracySummaryDto.toEntity() = AccuracySummaryEntity(
    areaId = areaId,
    accuracyScore = accuracyScore,
    sampleSize = sampleSize,
    windowHours = windowHours,
    calculatedAt = calculatedAt,
)

fun CurrentStageDto.toEntity() = CurrentStageEntity(stage = stage, updatedAt = updatedAt)
