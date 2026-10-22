package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.AreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AreaDao {

    // Upsert rather than REPLACE: REPLACE deletes the row first, which would cascade to schedule events
    // and break the favourites foreign key.
    @Upsert
    suspend fun upsertAll(areas: List<AreaEntity>)

    @Query("SELECT * FROM areas WHERE areaId = :areaId")
    suspend fun getById(areaId: String): AreaEntity?

    @Query("SELECT * FROM areas WHERE areaId = :areaId")
    fun observeById(areaId: String): Flow<AreaEntity?>

    // Matches suburb name or municipality, as the API search does
    @Query(
        """
        SELECT * FROM areas
        WHERE isActive = 1 AND (name LIKE '%' || :query || '%' OR municipality LIKE '%' || :query || '%')
        ORDER BY name
        """,
    )
    fun search(query: String): Flow<List<AreaEntity>>

    // One-shot version of search for the offline fallback when the API cannot be reached
    @Query(
        """
        SELECT * FROM areas
        WHERE isActive = 1 AND (name LIKE '%' || :query || '%' OR municipality LIKE '%' || :query || '%')
        ORDER BY name
        LIMIT :limit
        """,
    )
    suspend fun searchOnce(query: String, limit: Int): List<AreaEntity>

    @Query("SELECT COUNT(*) FROM areas")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(area: AreaEntity)

    @Query(
        """
        UPDATE areas SET name = :name, municipality = :municipality, province = :province, supplyType = :supplyType
        WHERE areaId = :areaId
        """,
    )
    suspend fun updateSummary(areaId: String, name: String, municipality: String, province: String, supplyType: String): Int

    // Caches search results, which carry no coordinates. Existing rows keep their coordinates and active flag;
    // new rows are inserted as active with unknown coordinates until the full area is fetched.
    @Transaction
    suspend fun cacheSummaries(areas: List<AreaEntity>) {
        areas.forEach { area ->
            val updated = updateSummary(area.areaId, area.name, area.municipality, area.province, area.supplyType)
            if (updated == 0) {
                insertIfAbsent(area)
            }
        }
    }
}
