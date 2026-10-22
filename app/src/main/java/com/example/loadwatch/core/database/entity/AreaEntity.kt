package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Public catalogue entity, cached locally in full. supplyType is ESKOM or MUNICIPAL.
@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey val areaId: String,
    val name: String,
    val municipality: String,
    val province: String,
    val supplyType: String,
    val latitude: Double?,
    val longitude: Double?,
    val isActive: Boolean,
)
