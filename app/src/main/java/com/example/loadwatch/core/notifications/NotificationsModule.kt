package com.example.loadwatch.core.notifications

import com.example.loadwatch.data.alert.StageAlertPresenter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    abstract fun bindPushMessaging(impl: FirebasePushMessaging): PushMessaging

    @Binds
    abstract fun bindPushPreferences(impl: SharedPushPreferences): PushPreferences

    @Binds
    abstract fun bindStageAlertPresenter(impl: StageAlertNotifier): StageAlertPresenter
}
