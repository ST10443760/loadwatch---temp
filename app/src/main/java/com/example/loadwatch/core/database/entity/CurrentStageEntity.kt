package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// The national load-shedding stage. A single row, id 1, mirroring the API's stage table.
@Entity(tableName = "current_stage")
data class CurrentStageEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val stage: Int,
    val updatedAt: Instant,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
