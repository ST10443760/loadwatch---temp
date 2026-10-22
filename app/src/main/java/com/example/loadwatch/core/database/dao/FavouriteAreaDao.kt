package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteAreaDao {

    @Upsert
    suspend fun upsertAll(favourites: List<FavouriteAreaEntity>)

    @Query("SELECT * FROM favourite_areas WHERE userId = :userId ORDER BY sortOrder, createdAt")
    fun observe(userId: String): Flow<List<FavouriteAreaEntity>>

    // Removes only the favourite row; the Area stays cached (FR-FAV-03)
    @Query("DELETE FROM favourite_areas WHERE userId = :userId AND areaId = :areaId")
    suspend fun delete(userId: String, areaId: String): Int

    @Query("DELETE FROM favourite_areas WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM favourite_areas")
    suspend fun deleteAll()
}
