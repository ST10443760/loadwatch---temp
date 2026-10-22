package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// Joins a user to an area. Unique on (userId, areaId). Deleting a favourite never deletes the Area (FR-FAV-03),
// and an Area cannot be removed from the cache while it is still a favourite.
@Entity(
    tableName = "favourite_areas",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["areaId"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["userId", "areaId"], unique = true),
        Index(value = ["areaId"]),
    ],
)
data class FavouriteAreaEntity(
    @PrimaryKey val favouriteId: String,
    val userId: String,
    val areaId: String,
    val label: String,
    val sortOrder: Int,
    val createdAt: Instant,
)
