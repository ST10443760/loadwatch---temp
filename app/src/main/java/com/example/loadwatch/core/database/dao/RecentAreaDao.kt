package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.RecentAreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentAreaDao {

    @Upsert
    suspend fun upsert(recent: RecentAreaEntity)

    @Query(
        """
        DELETE FROM recent_areas
        WHERE areaId NOT IN (SELECT areaId FROM recent_areas ORDER BY selectedAt DESC LIMIT :keep)
        """,
    )
    suspend fun trimTo(keep: Int)

    // Records a selection and keeps only the most recent `keep` areas
    @Transaction
    suspend fun record(recent: RecentAreaEntity, keep: Int) {
        upsert(recent)
        trimTo(keep)
    }

    @Query(
        """
        SELECT areas.* FROM recent_areas
        INNER JOIN areas ON areas.areaId = recent_areas.areaId
        ORDER BY recent_areas.selectedAt DESC
        """,
    )
    fun observeRecentAreas(): Flow<List<AreaEntity>>

    @Query("DELETE FROM recent_areas")
    suspend fun deleteAll()
}
