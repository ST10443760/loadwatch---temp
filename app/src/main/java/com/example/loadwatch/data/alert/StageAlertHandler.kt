package com.example.loadwatch.data.alert

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.data.repository.ScheduleRepository
import com.example.loadwatch.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

// Shows a stage-change alert on the phone. Implemented with Android notifications; faked in tests.
interface StageAlertPresenter {
    // languageCode is the user's S09 language, so the text matches the app even when it is not running
    fun show(alert: StageAlert, nextOutageMinutes: Long?, languageCode: String)
}

// Decides whether an incoming stage change reaches the user, from the settings and schedule saved on the phone, so
// it works even when the app was closed and the phone is offline apart from FCM
@Singleton
class StageAlertHandler @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduleRepository: ScheduleRepository,
    private val presenter: StageAlertPresenter,
    private val clock: Clock,
) {
    suspend fun handle(alert: StageAlert) {
        Log.i(LogTags.PUSH, "Stage alert for ${alert.areaId}: ${alert.previousStage} -> ${alert.stage}")
        val settings = settingsRepository.observeSettings().first()
        val now = clock.instant()
        val upcoming = scheduleRepository.observeAreaSchedule(alert.areaId, now).first().events

        when (val decision = StageAlertPolicy.decide(alert, settings, upcoming, now)) {
            is StageAlertDecision.Show -> {
                Log.i(LogTags.PUSH, "Showing the stage alert (next outage in ${decision.nextOutageMinutes ?: "-"} min)")
                presenter.show(alert, decision.nextOutageMinutes, settings?.languageCode ?: "en")
            }
            is StageAlertDecision.Skip -> Log.i(LogTags.PUSH, "Stage alert not shown: ${decision.reason}")
        }
    }
}
