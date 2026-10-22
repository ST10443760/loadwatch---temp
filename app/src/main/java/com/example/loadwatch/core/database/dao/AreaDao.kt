package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
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

    @Query("SELECT COUNT(*) FROM areas")
    suspend fun count(): Int
}
