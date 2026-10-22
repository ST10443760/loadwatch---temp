package com.example.loadwatch.data.local

import com.example.loadwatch.core.database.dao.UserSettingsDao
import com.example.loadwatch.core.database.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Room-backed settings. Screens render from here first and update when the network answers.
class SettingsLocalDataSource @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
) {
    suspend fun get(userId: String): UserSettingsEntity? = userSettingsDao.get(userId)

    suspend fun defaultAreaId(userId: String): String? = userSettingsDao.get(userId)?.defaultAreaId

    fun observe(userId: String): Flow<UserSettingsEntity?> = userSettingsDao.observe(userId)

    suspend fun save(settings: UserSettingsEntity) = userSettingsDao.upsert(settings)
}
