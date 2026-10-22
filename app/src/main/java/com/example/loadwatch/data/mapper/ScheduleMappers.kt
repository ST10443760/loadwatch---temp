package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.network.dto.ScheduleEventDto

fun ScheduleEventDto.toEntity() = ScheduleEventEntity(
    scheduleId = scheduleId,
    areaId = areaId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    source = source,
    updatedAt = updatedAt,
)
