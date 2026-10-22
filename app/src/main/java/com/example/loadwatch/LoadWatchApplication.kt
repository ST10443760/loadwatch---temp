package com.example.loadwatch

import android.app.Application
import android.util.Log
import com.example.loadwatch.core.common.LogTags
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LoadWatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(LogTags.LIFECYCLE, "Application onCreate (version ${BuildConfig.VERSION_NAME})")
    }
}
