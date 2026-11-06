package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.loadwatch.core.common.OutageEventType
import java.time.Instant

// A community report from the API's history, cached per area and month (yyyy-MM). It has no reporter, unlike the
// user's own reports in outage_reports.
@Entity(
    tableName = "history_reports",
    indices = [Index(value = ["areaId", "month"])],
)
data class HistoryReportEntity(
    @PrimaryKey val reportId: String,
    val areaId: String,
    val month: String,
    val eventType: OutageEventType,
    val reportedAt: Instant,
)
