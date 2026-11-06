package com.example.loadwatch

import android.app.Application
import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.notifications.NotificationChannels
import com.example.loadwatch.data.repository.PushSync
import com.example.loadwatch.data.sync.SyncStarter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Application entry point and Hilt root. On start it creates the stage alert notification channel, keeps the
 * FCM token and area topic in step with the session, and starts a sync of anything still queued from offline
 * use.
 */
@HiltAndroidApp
class LoadWatchApplication : Application() {

    @Inject lateinit var pushSync: PushSync

    @Inject lateinit var syncStarter: SyncStarter

    // Work that lasts as long as the app process, such as following the selected area's FCM topic and starting
    // a sync of anything queued offline
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.i(LogTags.LIFECYCLE, "Application onCreate (version ${BuildConfig.VERSION_NAME})")
        // Before any FCM message can arrive, so the first stage alert already has its channel
        NotificationChannels.createStageAlertChannel(this)
        pushSync.start(applicationScope)
        syncStarter.start(applicationScope)
    }
}
