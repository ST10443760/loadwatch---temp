package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.HistoryMonthEntity
import com.example.loadwatch.core.database.entity.HistoryOutageEntity
import com.example.loadwatch.core.database.entity.HistoryReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_outages WHERE areaId = :areaId AND month = :month ORDER BY startAt")
    fun observeOutages(areaId: String, month: String): Flow<List<HistoryOutageEntity>>

    @Query("SELECT * FROM history_reports WHERE areaId = :areaId AND month = :month ORDER BY reportedAt")
    fun observeReports(areaId: String, month: String): Flow<List<HistoryReportEntity>>

    @Query("SELECT * FROM history_months WHERE areaId = :areaId AND month = :month")
    fun observeMonth(areaId: String, month: String): Flow<HistoryMonthEntity?>

    @Query("DELETE FROM history_outages WHERE areaId = :areaId AND month = :month")
    suspend fun deleteOutages(areaId: String, month: String)

    @Query("DELETE FROM history_reports WHERE areaId = :areaId AND month = :month")
    suspend fun deleteReports(areaId: String, month: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutages(outages: List<HistoryOutageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<HistoryReportEntity>)

    @Upsert
    suspend fun upsertMonth(month: HistoryMonthEntity)

    // Swaps an area's cached month for the API's copy in one step, so the screen never sees half a month
    @Transaction
    suspend fun replaceMonth(
        month: HistoryMonthEntity,
        outages: List<HistoryOutageEntity>,
        reports: List<HistoryReportEntity>,
    ) {
        deleteOutages(month.areaId, month.month)
        deleteReports(month.areaId, month.month)
        insertOutages(outages)
        insertReports(reports)
        upsertMonth(month)
    }
}
