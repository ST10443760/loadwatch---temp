package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import java.time.Instant

// Records that an area's month (yyyy-MM) has been fetched, so an empty month can be told apart from one never loaded
@Entity(
    tableName = "history_months",
    primaryKeys = ["areaId", "month"],
)
data class HistoryMonthEntity(
    val areaId: String,
    val month: String,
    val fetchedAt: Instant,
)
