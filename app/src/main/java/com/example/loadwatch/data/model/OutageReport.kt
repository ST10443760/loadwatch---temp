package com.example.loadwatch.data.model

import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import java.time.Instant

// A community confirmation as S07 shows it in the user's recent reports
data class OutageReport(
    val reportId: String,
    val clientReportId: String,
    val areaId: String,
    val eventType: OutageEventType,
    val reportedAt: Instant,
    val syncStatus: SyncStatus,
)
