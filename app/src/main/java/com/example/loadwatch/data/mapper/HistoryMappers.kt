package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.HistoryOutageEntity
import com.example.loadwatch.core.database.entity.HistoryReportEntity
import com.example.loadwatch.core.network.dto.HistoryOutageDto
import com.example.loadwatch.core.network.dto.HistoryReportDto
import com.example.loadwatch.data.model.HistoryOutage
import com.example.loadwatch.data.model.HistoryReport

/**
 * Maps a history outage from the API to its Room row, tagged with the area and month (yyyy-MM) it was fetched
 * for.
 */
fun HistoryOutageDto.toEntity(areaId: String, month: String) = HistoryOutageEntity(
    scheduleId = scheduleId,
    areaId = areaId,
    month = month,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    reportCount = reportCount,
)

/**
 * Maps a history community report from the API to its Room row, tagged with the area and month it was fetched
 * for.
 */
fun HistoryReportDto.toEntity(areaId: String, month: String) = HistoryReportEntity(
    reportId = reportId,
    areaId = areaId,
    month = month,
    eventType = eventType,
    reportedAt = reportedAt,
)

/** Maps a cached history outage to the domain model. */
fun HistoryOutageEntity.toModel() = HistoryOutage(
    scheduleId = scheduleId,
    stage = stage,
    startAt = startAt,
    endAt = endAt,
    reportCount = reportCount,
)

/** Maps a cached history report to the domain model. */
fun HistoryReportEntity.toModel() = HistoryReport(
    reportId = reportId,
    eventType = eventType,
    reportedAt = reportedAt,
)
