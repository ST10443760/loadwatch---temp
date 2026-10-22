package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ScheduleEventDao {

    @Upsert
    suspend fun upsertAll(events: List<ScheduleEventEntity>)

    // Every event overlapping the window, matching GET /api/schedules
    @Query(
        """
        SELECT * FROM schedule_events
        WHERE areaId = :areaId AND startAt < :to AND endAt > :from
        ORDER BY startAt
        """,
    )
    fun observeWindow(areaId: String, from: Instant, to: Instant): Flow<List<ScheduleEventEntity>>

    // The event in progress, or else the next one, so the countdown can run offline (FR-COUNT-01)
    @Query(
        """
        SELECT * FROM schedule_events
        WHERE areaId = :areaId AND endAt > :now
        ORDER BY startAt
        LIMIT 1
        """,
    )
    fun observeCurrentOrNext(areaId: String, now: Instant): Flow<ScheduleEventEntity?>

    @Query("SELECT MAX(updatedAt) FROM schedule_events WHERE areaId = :areaId")
    suspend fun lastUpdatedAt(areaId: String): Instant?

    @Query("DELETE FROM schedule_events WHERE endAt < :cutoff")
    suspend fun deleteEndedBefore(cutoff: Instant): Int
}
