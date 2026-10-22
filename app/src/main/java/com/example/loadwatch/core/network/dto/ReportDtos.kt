package com.example.loadwatch.core.network.dto

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.network.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

// POST /api/outage-reports body (OutageReportCreate, Table 7). clientReportId makes the POST idempotent,
// which is what allows a queued report to be retried safely.
@Serializable
data class OutageReportCreateDto(
    val areaId: String,
    val eventType: OutageEventType,
    @Serializable(with = InstantSerializer::class) val reportedAt: Instant,
    @Serializable(with = InstantSerializer::class) val clientCreatedAt: Instant,
    val clientReportId: String,
)

// POST /api/outage-reports returns 201 (new) or 200 (retry of the same clientReportId) with OutageReport;
// GET /api/areas/{areaId}/outage-reports?since= returns OutageReport[]
@Serializable
data class OutageReportDto(
    val reportId: String,
    val clientReportId: String,
    val userId: String,
    val areaId: String,
    val eventType: OutageEventType,
    @Serializable(with = InstantSerializer::class) val reportedAt: Instant,
    @Serializable(with = InstantSerializer::class) val clientCreatedAt: Instant,
    val syncStatus: SyncStatus,
)

// GET /api/areas/{areaId}/accuracy returns AccuracySummary; accuracyScore is between 0.00 and 1.00
@Serializable
data class AccuracySummaryDto(
    val areaId: String,
    val accuracyScore: Double,
    val sampleSize: Int,
    val windowHours: Int,
    @Serializable(with = InstantSerializer::class) val calculatedAt: Instant,
)
