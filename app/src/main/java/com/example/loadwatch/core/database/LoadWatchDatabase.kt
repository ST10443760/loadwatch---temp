package com.example.loadwatch.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.loadwatch.core.database.converter.InstantConverter
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.dao.ScheduleEventDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.core.database.entity.PendingSyncEntity
import com.example.loadwatch.core.database.entity.RecentAreaEntity
import com.example.loadwatch.core.database.entity.ScheduleEventEntity
import com.example.loadwatch.core.database.entity.UserSettingsEntity

@Database(
    entities = [
        AreaEntity::class,
        ScheduleEventEntity::class,
        UserSettingsEntity::class,
        FavouriteAreaEntity::class,
        OutageReportEntity::class,
        PendingSyncEntity::class,
        RecentAreaEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 2: adds recent_areas for the area selector
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(InstantConverter::class)
abstract class LoadWatchDatabase : RoomDatabase() {
    abstract fun areaDao(): AreaDao
    abstract fun scheduleEventDao(): ScheduleEventDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun favouriteAreaDao(): FavouriteAreaDao
    abstract fun outageReportDao(): OutageReportDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun recentAreaDao(): RecentAreaDao

    companion object {
        const val NAME = "loadwatch.db"
    }
}
