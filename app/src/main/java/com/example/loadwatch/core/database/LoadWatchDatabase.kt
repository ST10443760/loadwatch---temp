package com.example.loadwatch.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.loadwatch.core.database.converter.InstantConverter
import com.example.loadwatch.core.database.dao.AreaDao
import com.example.loadwatch.core.database.dao.AreaStatusDao
import com.example.loadwatch.core.database.dao.FavouriteAreaDao
import com.example.loadwatch.core.database.dao.HistoryDao
import com.example.loadwatch.core.database.dao.OutageReportDao
import com.example.loadwatch.core.database.dao.PendingSyncDao
import com.example.loadwatch.core.database.dao.RecentAreaDao
import com.example.loadwatch.core.database.dao.ScheduleEventDao
import com.example.loadwatch.core.database.dao.UserSettingsDao
import com.example.loadwatch.core.database.entity.AccuracySummaryEntity
import com.example.loadwatch.core.database.entity.AreaEntity
import com.example.loadwatch.core.database.entity.AreaRefreshEntity
import com.example.loadwatch.core.database.entity.CurrentStageEntity
import com.example.loadwatch.core.database.entity.FavouriteAreaEntity
import com.example.loadwatch.core.database.entity.HistoryMonthEntity
import com.example.loadwatch.core.database.entity.HistoryOutageEntity
import com.example.loadwatch.core.database.entity.HistoryReportEntity
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
        AccuracySummaryEntity::class,
        AreaRefreshEntity::class,
        CurrentStageEntity::class,
        HistoryOutageEntity::class,
        HistoryReportEntity::class,
        HistoryMonthEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // 2: adds recent_areas for the area selector
        AutoMigration(from = 1, to = 2),
        // 3: adds accuracy summaries, per-area refresh state and the current stage for Home and Schedule
        AutoMigration(from = 2, to = 3),
        // 4: adds the cached outage history (history_outages, history_reports, history_months)
        AutoMigration(from = 3, to = 4),
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
    abstract fun areaStatusDao(): AreaStatusDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "loadwatch.db"
    }
}
