package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.PendingSyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface PendingSyncDao {

    @Insert
    suspend fun insert(item: PendingSyncEntity)

    @Update
    suspend fun update(item: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE syncId = :syncId")
    suspend fun delete(syncId: String)

    // Work that is ready to retry; rejected items stay visible but are never retried automatically
    @Query(
        """
        SELECT * FROM pending_sync
        WHERE status != :rejected AND nextAttemptAt <= :now
        ORDER BY nextAttemptAt
        """,
    )
    suspend fun getDue(now: Instant, rejected: PendingSyncStatus = PendingSyncStatus.REJECTED): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync ORDER BY nextAttemptAt")
    fun observeAll(): Flow<List<PendingSyncEntity>>

    @Query("DELETE FROM pending_sync")
    suspend fun deleteAll()
}
