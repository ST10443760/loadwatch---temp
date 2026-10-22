package com.example.loadwatch.data

import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.OfflineFirstAreaRepository
import com.example.loadwatch.data.repository.OfflineFirstReportRepository
import com.example.loadwatch.data.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindAreaRepository(impl: OfflineFirstAreaRepository): AreaRepository

    @Binds
    abstract fun bindReportRepository(impl: OfflineFirstReportRepository): ReportRepository
}
