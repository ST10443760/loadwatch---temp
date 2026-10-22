package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// Areas the signed-in user recently switched to, for S05 Area Selector. User-scoped, so cleared on sign-out.
@Entity(
    tableName = "recent_areas",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["selectedAt"])],
)
data class RecentAreaEntity(
    @PrimaryKey val areaId: String,
    val selectedAt: Instant,
)
