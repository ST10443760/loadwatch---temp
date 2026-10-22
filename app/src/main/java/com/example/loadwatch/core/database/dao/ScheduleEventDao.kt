package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM schedule_events WHERE areaId = :areaId")
    suspend fun deleteForArea(areaId: String)

    // Swaps an area's cached schedule for a freshly fetched window, atomically, so a reader never sees
    // a half-replaced schedule
    @Transaction
    suspend fun replaceForArea(areaId: String, events: List<ScheduleEventEntity>) {
        deleteForArea(areaId)
        upsertAll(events)
    }

    // Windows that have not finished before `from`, for the countdown, which needs the current and next window
    @Query("SELECT * FROM schedule_events WHERE areaId = :areaId AND endAt > :from ORDER BY startAt")
    fun observeEndingAfter(areaId: String, from: Instant): Flow<List<ScheduleEventEntity>>
}
