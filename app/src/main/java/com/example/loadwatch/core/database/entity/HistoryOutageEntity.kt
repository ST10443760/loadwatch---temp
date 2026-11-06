package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// A past scheduled outage from the API's history, cached per area and month (yyyy-MM) so the history works offline.
// No foreign key to the area catalogue: this is a read-only copy that is replaced whole on each refresh.
@Entity(
    tableName = "history_outages",
    indices = [Index(value = ["areaId", "month"])],
)
data class HistoryOutageEntity(
    @PrimaryKey val scheduleId: String,
    val areaId: String,
    val month: String,
    val stage: Int,
    val startAt: Instant,
    val endAt: Instant,
    // Community reports that fall inside the outage
    val reportCount: Int,
)
