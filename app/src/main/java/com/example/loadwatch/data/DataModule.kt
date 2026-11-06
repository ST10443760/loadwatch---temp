package com.example.loadwatch.data

import com.example.loadwatch.data.repository.AreaRepository
import com.example.loadwatch.data.repository.FavouritesRepository
import com.example.loadwatch.data.repository.OfflineFirstAreaRepository
import com.example.loadwatch.data.repository.OfflineFirstFavouritesRepository
import com.example.loadwatch.data.repository.OfflineFirstReportRepository
import com.example.loadwatch.data.repository.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt bindings from the repository interfaces to their offline-first implementations, so view models depend
 * only on the interfaces and tests can use fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindAreaRepository(impl: OfflineFirstAreaRepository): AreaRepository

    @Binds
    abstract fun bindReportRepository(impl: OfflineFirstReportRepository): ReportRepository

    @Binds
    abstract fun bindFavouritesRepository(impl: OfflineFirstFavouritesRepository): FavouritesRepository
}
