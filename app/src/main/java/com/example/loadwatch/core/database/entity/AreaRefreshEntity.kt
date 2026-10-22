package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

// When an area's schedule was last refreshed, and what that refresh learned (FR-SCHED-04).
@Entity(
    tableName = "area_refresh",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AreaRefreshEntity(
    @PrimaryKey val areaId: String,
    val lastRefreshedAt: Instant,
    // The schedule window that was fetched; days outside it are unknown rather than clear
    val windowStart: Instant,
    val windowEnd: Instant,
    // serverTime minus the device clock when the response arrived, applied to every countdown tick
    val clockOffsetMillis: Long,
    // The API's next event, kept in case it falls beyond the cached window
    val nextStage: Int?,
    val nextStartAt: Instant?,
    val nextEndAt: Instant?,
)
