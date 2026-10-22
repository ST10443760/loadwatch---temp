package com.example.loadwatch.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

// Queued work waiting to reach the API. Device only, never synchronised to the server, and generic by
// design so future entities can reuse the queue.
@Entity(
    tableName = "pending_sync",
    indices = [Index(value = ["status", "nextAttemptAt"])],
)
data class PendingSyncEntity(
    @PrimaryKey val syncId: String,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val retryCount: Int,
    val nextAttemptAt: Instant,
    val status: PendingSyncStatus,
    val lastError: String?,
)

enum class PendingSyncStatus {
    PENDING,
    RETRYING,
    REJECTED,
}
