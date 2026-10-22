package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

// Table 8 AccuracySummary: an area's community accuracy score, cached so Home shows it offline.
// accuracyScore is between 0.00 and 1.00.
@Entity(
    tableName = "accuracy_summaries",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AccuracySummaryEntity(
    @PrimaryKey val areaId: String,
    val accuracyScore: Double,
    val sampleSize: Int,
    val windowHours: Int,
    val calculatedAt: Instant,
)
