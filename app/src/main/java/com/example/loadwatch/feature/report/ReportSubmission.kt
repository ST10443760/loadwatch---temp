package com.example.loadwatch.feature.report

import android.util.Log
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.repository.ReportRepository
import com.example.loadwatch.data.repository.ReportResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// The lifecycle shown in S07's submission status card (FR-REP-01 to 04)
sealed interface SubmissionStatus {
    data object Idle : SubmissionStatus
    data class Sending(val eventType: OutageEventType?) : SubmissionStatus
    data class Accepted(val eventType: OutageEventType) : SubmissionStatus

    // 409: a friendly outcome, the event is already counted
    data class AlreadyReported(val eventType: OutageEventType) : SubmissionStatus

    // No connection: kept on the phone as PENDING
    data class SavedOffline(val eventType: OutageEventType?) : SubmissionStatus

    // 400 from the API
    data class Rejected(val eventType: OutageEventType, val fields: List<String>) : SubmissionStatus

    // 401, or nobody signed in
    data class NotSignedIn(val eventType: OutageEventType?) : SubmissionStatus

    data class Failed(val eventType: OutageEventType?, val failure: DataFailure) : SubmissionStatus
}

// Sends community confirmations one at a time. A second tap while one is in flight is ignored, so a double tap
// can never send two reports.
class ReportSubmission(
    private val repository: ReportRepository,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<SubmissionStatus>(SubmissionStatus.Idle)
    val status: StateFlow<SubmissionStatus> = _status.asStateFlow()

    val isBusy: Boolean get() = _status.value is SubmissionStatus.Sending

    // Returns false when ignored because a report is already being sent
    fun submit(areaId: String, eventType: OutageEventType): Boolean {
        if (isBusy) {
            Log.d(LogTags.API, "Ignored a $eventType tap while a report is in flight")
            return false
        }
        // Set before launching, so a tap that arrives before the coroutine starts is still blocked
        _status.value = SubmissionStatus.Sending(eventType)
        scope.launch {
            _status.value = repository.submit(areaId, eventType).toStatus(eventType)
            Log.i(LogTags.API, "Report submission finished: ${_status.value.javaClass.simpleName}")
        }
        return true
    }

    // Recovers from the last outcome: a 400 is submitted again, anything else re-sends the area's PENDING reports
    fun retry(areaId: String) {
        val current = _status.value
        if (current is SubmissionStatus.Rejected) {
            submit(areaId, current.eventType)
            return
        }
        retryUnsent(areaId, eventType = current.eventTypeOrNull())
    }

    // The manual "Retry sending" for PENDING reports; the automatic sync worker is Final POE scope
    fun retryUnsent(areaId: String, eventType: OutageEventType? = null) {
        if (isBusy) return
        _status.value = SubmissionStatus.Sending(eventType)
        scope.launch {
            val result = repository.retryUnsent(areaId)
            _status.value = result?.toStatus(eventType) ?: SubmissionStatus.Idle
        }
    }

    private fun ReportResult.toStatus(eventType: OutageEventType?): SubmissionStatus = when (this) {
        is ReportResult.Accepted -> SubmissionStatus.Accepted(report.eventType)
        ReportResult.AlreadyReported -> SubmissionStatus.AlreadyReported(eventType ?: OutageEventType.POWER_OFF)
        is ReportResult.SavedOffline -> SubmissionStatus.SavedOffline(report.eventType)
        is ReportResult.Rejected -> SubmissionStatus.Rejected(eventType ?: OutageEventType.POWER_OFF, fields)
        ReportResult.NotSignedIn -> SubmissionStatus.NotSignedIn(eventType)
        is ReportResult.Failed -> SubmissionStatus.Failed(eventType, failure)
    }

    private fun SubmissionStatus.eventTypeOrNull(): OutageEventType? = when (this) {
        SubmissionStatus.Idle -> null
        is SubmissionStatus.Sending -> eventType
        is SubmissionStatus.Accepted -> eventType
        is SubmissionStatus.AlreadyReported -> eventType
        is SubmissionStatus.SavedOffline -> eventType
        is SubmissionStatus.Rejected -> eventType
        is SubmissionStatus.NotSignedIn -> eventType
        is SubmissionStatus.Failed -> eventType
    }
}
