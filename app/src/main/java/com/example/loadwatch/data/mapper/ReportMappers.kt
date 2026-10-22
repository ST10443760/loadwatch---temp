package com.example.loadwatch.data.mapper

import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.core.network.dto.OutageReportCreateDto
import com.example.loadwatch.core.network.dto.OutageReportDto

fun OutageReportDto.toEntity() = OutageReportEntity(
    reportId = reportId,
    clientReportId = clientReportId,
    userId = userId,
    areaId = areaId,
    eventType = eventType,
    reportedAt = reportedAt,
    clientCreatedAt = clientCreatedAt,
    syncStatus = syncStatus,
)

// A locally queued report becomes the create request; the server assigns its own reportId
fun OutageReportEntity.toCreateDto() = OutageReportCreateDto(
    areaId = areaId,
    eventType = eventType,
    reportedAt = reportedAt,
    clientCreatedAt = clientCreatedAt,
    clientReportId = clientReportId,
)
