package com.example.loadwatch.data.local

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.TransactionRunner
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import javax.inject.Inject

// Everything in Room that belongs to the signed-in user. The public area catalogue and schedules are
// deliberately not reachable from here, so clearing user data can never remove them (FR-AUTH-04).
class UserDataLocalDataSource @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val userSettingsDao: UserSettingsDao,
    private val favouriteAreaDao: FavouriteAreaDao,
    private val outageReportDao: OutageReportDao,
    private val pendingSyncDao: PendingSyncDao,
    private val recentAreaDao: RecentAreaDao,
) {
    suspend fun clearAll() {
        transactionRunner.inTransaction {
            userSettingsDao.deleteAll()
            favouriteAreaDao.deleteAll()
            outageReportDao.deleteAll()
            pendingSyncDao.deleteAll()
            recentAreaDao.deleteAll()
        }
        Log.i(LogTags.ROOM, "Cleared user-scoped data; the area catalogue and schedules were kept")
    }
}
