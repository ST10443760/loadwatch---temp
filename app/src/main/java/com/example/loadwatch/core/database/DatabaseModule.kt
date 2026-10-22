package com.example.loadwatch.core.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.AreaStatusDao
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.dao.ScheduleEventDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LoadWatchDatabase =
        Room.databaseBuilder(context, LoadWatchDatabase::class.java, LoadWatchDatabase.NAME)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        Log.i(LogTags.ROOM, "Created database ${LoadWatchDatabase.NAME} at version ${db.version}")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        Log.i(LogTags.ROOM, "Opened database ${LoadWatchDatabase.NAME} at version ${db.version}")
                    }
                },
            )
            .build()

    @Provides fun provideAreaDao(db: LoadWatchDatabase): AreaDao = db.areaDao()

    @Provides fun provideScheduleEventDao(db: LoadWatchDatabase): ScheduleEventDao = db.scheduleEventDao()

    @Provides fun provideUserSettingsDao(db: LoadWatchDatabase): UserSettingsDao = db.userSettingsDao()

    @Provides fun provideFavouriteAreaDao(db: LoadWatchDatabase): FavouriteAreaDao = db.favouriteAreaDao()

    @Provides fun provideOutageReportDao(db: LoadWatchDatabase): OutageReportDao = db.outageReportDao()

    @Provides fun providePendingSyncDao(db: LoadWatchDatabase): PendingSyncDao = db.pendingSyncDao()

    @Provides fun provideRecentAreaDao(db: LoadWatchDatabase): RecentAreaDao = db.recentAreaDao()

    @Provides fun provideAreaStatusDao(db: LoadWatchDatabase): AreaStatusDao = db.areaStatusDao()

    @Provides fun provideTransactionRunner(db: LoadWatchDatabase): TransactionRunner = RoomTransactionRunner(db)
}
