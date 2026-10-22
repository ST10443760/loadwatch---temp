package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.database.entity.OutageReportEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface OutageReportDao {

    // Aborts on a repeated (userId, clientReportId) so a double tap cannot queue the same report twice
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: OutageReportEntity)

    @Upsert
    suspend fun upsertAll(reports: List<OutageReportEntity>)

    @Query("SELECT * FROM outage_reports WHERE userId = :userId AND clientReportId = :clientReportId")
    suspend fun getByClientReportId(userId: String, clientReportId: String): OutageReportEntity?

    @Query("SELECT * FROM outage_reports WHERE areaId = :areaId AND reportedAt >= :since ORDER BY reportedAt DESC")
    fun observeForArea(areaId: String, since: Instant): Flow<List<OutageReportEntity>>

    @Query("SELECT * FROM outage_reports WHERE syncStatus IN (:statuses) ORDER BY clientCreatedAt")
    suspend fun getByStatus(statuses: List<SyncStatus>): List<OutageReportEntity>

    @Query("UPDATE outage_reports SET syncStatus = :status WHERE reportId = :reportId")
    suspend fun updateSyncStatus(reportId: String, status: SyncStatus)
}
