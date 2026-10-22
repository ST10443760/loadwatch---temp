package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import java.time.Instant

// A single structured community confirmation. clientReportId is unique per user and makes the POST
// idempotent, so a queued report can be retried safely.
@Entity(
    tableName = "outage_reports",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["userId", "clientReportId"], unique = true),
        Index(value = ["areaId", "reportedAt"]),
        Index(value = ["syncStatus"]),
    ],
)
data class OutageReportEntity(
    @PrimaryKey val reportId: String,
    val clientReportId: String,
    val userId: String,
    val areaId: String,
    val eventType: OutageEventType,
    val reportedAt: Instant,
    val clientCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
