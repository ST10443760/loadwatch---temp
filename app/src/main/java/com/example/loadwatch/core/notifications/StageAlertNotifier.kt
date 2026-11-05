package com.example.loadwatch.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.loadwatch.MainActivity
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.alert.StageAlert
import com.example.loadwatch.data.alert.StageAlertPresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// Posts a stage-change alert in the user's chosen language. If notifications are not allowed (denied on Android 13+,
// or turned off in system settings) the alert is logged and dropped; nothing is retried or forced.
@Singleton
class StageAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : StageAlertPresenter {

    override fun show(alert: StageAlert, nextOutageMinutes: Long?, languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(LogTags.PUSH, "Notification permission not granted; the stage alert was not shown")
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(LogTags.PUSH, "Notifications are off for LoadWatch; the stage alert was not shown")
            return
        }

        val resources = localizedResources(languageCode)
        val title = if (alert.stage == 0) {
            resources.getString(R.string.stage_alert_ended_title, alert.areaName)
        } else {
            resources.getString(R.string.stage_alert_title, alert.stage, alert.areaName)
        }
        val body = buildString {
            append(
                when {
                    alert.stage == 0 -> resources.getString(R.string.stage_alert_body_ended)
                    alert.previousStage == 0 -> resources.getString(R.string.stage_alert_body_started, alert.stage)
                    else -> resources.getString(R.string.stage_alert_body_changed, alert.previousStage, alert.stage)
                },
            )
            if (nextOutageMinutes != null) {
                val minutes = nextOutageMinutes.toInt()
                append(' ').append(resources.getQuantityString(R.plurals.stage_alert_next_outage, minutes, minutes))
            }
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.STAGE_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_stage_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(alert.changedAt.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()

        try {
            // One notification per area: a newer change replaces the older one rather than stacking up
            manager.notify(alert.areaId.hashCode(), notification)
            Log.i(LogTags.PUSH, "Stage alert shown for ${alert.areaId}")
        } catch (e: SecurityException) {
            Log.w(LogTags.PUSH, "Android refused the stage alert notification", e)
        }
    }

    // The app's own language, which may differ from the phone's and is not applied to a service on older Androids
    private fun localizedResources(languageCode: String): Resources {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageCode))
        return context.createConfigurationContext(configuration).resources
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
