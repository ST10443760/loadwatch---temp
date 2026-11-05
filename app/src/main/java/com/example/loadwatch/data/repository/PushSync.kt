package com.example.loadwatch.data.repository

import android.util.Log
import com.example.loadwatch.core.authentication.AuthSession
import com.example.loadwatch.core.common.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Runs for the life of the app: registers the FCM token whenever someone signs in, and follows the selected area's
// topic as the area changes. Signing out clears the settings, which leaves the topic.
@Singleton
class PushSync @Inject constructor(
    private val authSession: AuthSession,
    private val settingsRepository: SettingsRepository,
    private val pushRepository: PushRepository,
) {
    fun start(scope: CoroutineScope) {
        Log.d(LogTags.PUSH, "Push sync started")
        scope.launch {
            authSession.signedInUserId.filterNotNull().distinctUntilChanged().collect {
                pushRepository.registerDevice()
            }
        }
        scope.launch {
            settingsRepository.observeSettings()
                .map { it?.defaultAreaId }
                .distinctUntilChanged()
                .collectLatest { areaId -> pushRepository.followArea(areaId) }
        }
    }
}
