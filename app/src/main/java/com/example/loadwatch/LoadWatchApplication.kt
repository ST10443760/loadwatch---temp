package com.example.loadwatch

import android.app.Application
import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.notifications.NotificationChannels
import com.example.loadwatch.data.repository.PushSync
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class LoadWatchApplication : Application() {

    @Inject lateinit var pushSync: PushSync

    // Work that lasts as long as the app process, such as following the selected area's FCM topic
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i(LogTags.LIFECYCLE, "Application onCreate (version ${BuildConfig.VERSION_NAME})")
        // Before any FCM message can arrive, so the first stage alert already has its channel
        NotificationChannels.createStageAlertChannel(this)
        pushSync.start(applicationScope)
    }
}
