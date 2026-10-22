package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// One interruption window. endAt must exceed startAt; updatedAt drives the stale-data badge.
@Entity(
    tableName = "schedule_events",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["areaId", "startAt"])],
)
data class ScheduleEventEntity(
    @PrimaryKey val scheduleId: String,
    val areaId: String,
    val stage: Int,
    val startAt: Instant,
    val endAt: Instant,
    val source: String,
    val updatedAt: Instant,
)
