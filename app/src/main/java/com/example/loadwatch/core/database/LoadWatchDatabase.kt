package com.example.loadwatch.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.loadwatch.core.database.converter.InstantConverter
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.ScheduleEventDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.OutageReportEntity
import com.example.loadwatch.core.database.entity.PendingSyncEntity
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class LoadWatchDatabase : RoomDatabase() {
    abstract fun areaDao(): AreaDao
    abstract fun scheduleEventDao(): ScheduleEventDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun favouriteAreaDao(): FavouriteAreaDao
    abstract fun outageReportDao(): OutageReportDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        const val NAME = "loadwatch.db"
    }
}
