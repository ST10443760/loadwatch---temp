package com.example.loadwatch.core.notifications

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogTags

object NotificationChannels {
    const val STAGE_ALERTS = "stage_alerts"

    // Safe to call on every launch: Android keeps the user's own changes to an existing channel, and only the name
    // and description are refreshed (so they follow the app's language). Does nothing before Android 8.
    fun createStageAlertChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(STAGE_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.notification_channel_stage_alerts))
            .setDescription(context.getString(R.string.notification_channel_stage_alerts_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
        Log.d(LogTags.PUSH, "Stage alert notification channel ready")
    }
}
