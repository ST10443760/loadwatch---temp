package com.example.loadwatch.core.network.dto

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.network.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

// GET /api/areas/{areaId}/history?month=yyyy-MM. Only days with an outage or a report are listed; date is the
// South African date as yyyy-MM-dd.
@Serializable
data class AreaHistoryDto(
    val areaId: String,
    val month: String,
    val days: List<HistoryDayDto> = emptyList(),
)

@Serializable
data class HistoryDayDto(
    val date: String,
    val outages: List<HistoryOutageDto> = emptyList(),
    val reports: List<HistoryReportDto> = emptyList(),
)

// A scheduled outage that has started, with how many community reports fall inside it
@Serializable
data class HistoryOutageDto(
    val scheduleId: String,
    val stage: Int,
    @Serializable(with = InstantSerializer::class) val startAt: Instant,
    @Serializable(with = InstantSerializer::class) val endAt: Instant,
    val reportCount: Int,
)

@Serializable
data class HistoryReportDto(
    val reportId: String,
    val eventType: OutageEventType,
    @Serializable(with = InstantSerializer::class) val reportedAt: Instant,
)
