package com.example.loadwatch.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {

    @Upsert
    suspend fun upsert(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    suspend fun get(userId: String): UserSettingsEntity?

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    fun observe(userId: String): Flow<UserSettingsEntity?>

    // Sign-out clears user-scoped cache but not the public area catalogue (FR-AUTH-04)
    @Query("DELETE FROM user_settings WHERE userId = :userId")
    suspend fun delete(userId: String)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}
