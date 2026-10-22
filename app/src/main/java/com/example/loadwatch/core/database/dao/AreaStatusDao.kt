package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.AccuracySummaryEntity
import com.example.loadwatch.core.database.entity.AreaRefreshEntity
import com.example.loadwatch.core.database.entity.CurrentStageEntity
import kotlinx.coroutines.flow.Flow

// Refresh bookkeeping, accuracy and the national stage: everything Home shows besides the schedule itself
@Dao
interface AreaStatusDao {

    @Upsert
    suspend fun upsertRefresh(refresh: AreaRefreshEntity)

    @Query("SELECT * FROM area_refresh WHERE areaId = :areaId")
    suspend fun getRefresh(areaId: String): AreaRefreshEntity?

    @Query("SELECT * FROM area_refresh WHERE areaId = :areaId")
    fun observeRefresh(areaId: String): Flow<AreaRefreshEntity?>

    @Upsert
    suspend fun upsertAccuracy(accuracy: AccuracySummaryEntity)

    @Query("SELECT * FROM accuracy_summaries WHERE areaId = :areaId")
    fun observeAccuracy(areaId: String): Flow<AccuracySummaryEntity?>

    @Upsert
    suspend fun upsertCurrentStage(stage: CurrentStageEntity)

    @Query("SELECT * FROM current_stage WHERE id = 1")
    fun observeCurrentStage(): Flow<CurrentStageEntity?>
}
