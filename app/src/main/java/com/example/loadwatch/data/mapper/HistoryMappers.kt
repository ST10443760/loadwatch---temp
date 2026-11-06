package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.HistoryOutageEntity
import com.example.loadwatch.core.database.entity.HistoryReportEntity
import com.example.loadwatch.core.network.dto.HistoryOutageDto
import com.example.loadwatch.core.network.dto.HistoryReportDto
import com.example.loadwatch.data.model.HistoryOutage
import com.example.loadwatch.data.model.HistoryReport

fun HistoryOutageDto.toEntity(areaId: String, month: String) = HistoryOutageEntity(
    scheduleId = scheduleId,
    areaId = areaId,
    month = month,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    reportCount = reportCount,
)

fun HistoryReportDto.toEntity(areaId: String, month: String) = HistoryReportEntity(
    reportId = reportId,
    areaId = areaId,
    month = month,
    eventType = eventType,
    reportedAt = reportedAt,
)

fun HistoryOutageEntity.toModel() = HistoryOutage(
    scheduleId = scheduleId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    reportCount = reportCount,
)

fun HistoryReportEntity.toModel() = HistoryReport(
    reportId = reportId,
    eventType = eventType,
    reportedAt = reportedAt,
)
