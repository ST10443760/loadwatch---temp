package com.example.loadwatch.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

// The device clock and time zone, injected so time-dependent code can be tested with a fixed clock
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
