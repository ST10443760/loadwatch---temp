package com.example.loadwatch.feature.report

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.OutageReport
import com.example.loadwatch.data.repository.AccuracyScore
import com.example.loadwatch.data.repository.ReportRepository
import com.example.loadwatch.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfirmationUiState(
    val isLoading: Boolean = true,
    val area: Area? = null,
    val accuracy: AccuracyScore? = null,
    val accuracyRefreshFailed: Boolean = false,
    val recentReports: List<OutageReport> = emptyList(),
    val submission: SubmissionStatus = SubmissionStatus.Idle,
) {
    // Reports kept on the phone because they could not be sent yet
    val unsentCount: Int get() = recentReports.count { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.RETRYING }
    val isSending: Boolean get() = submission is SubmissionStatus.Sending
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CommunityConfirmationViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val submission = ReportSubmission(reportRepository, viewModelScope)
    private val accuracyRefreshFailed = MutableStateFlow(false)

    private val activeArea = settingsRepository.observeDefaultArea()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val areaId = activeArea.map { it?.areaId }.distinctUntilChanged()

    val uiState: StateFlow<ConfirmationUiState> =
        combine(
            activeArea,
            areaId.flatMapLatest { id -> if (id == null) flowOf(null) else reportRepository.observeAccuracy(id) },
            areaId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else reportRepository.observeRecentReports(id) },
            submission.status,
            accuracyRefreshFailed,
        ) { area, accuracy, reports, status, refreshFailed ->
            ConfirmationUiState(
                isLoading = false,
                area = area,
                accuracy = accuracy,
                accuracyRefreshFailed = refreshFailed,
                recentReports = reports,
                submission = status,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfirmationUiState())

    init {
        // Show the current score for the area as soon as the screen opens
        viewModelScope.launch {
            areaId.collectLatest { id -> if (id != null) refreshAccuracy(id) }
        }
    }

    fun report(eventType: OutageEventType) {
        val area = activeArea.value ?: return
        submission.submit(area.areaId, eventType)
    }

    fun retry() {
        val area = activeArea.value ?: return
        submission.retry(area.areaId)
    }

    fun retryUnsent() {
        val area = activeArea.value ?: return
        submission.retryUnsent(area.areaId)
    }

    fun refreshAccuracy() {
        val area = activeArea.value ?: return
        viewModelScope.launch { refreshAccuracy(area.areaId) }
    }

    private suspend fun refreshAccuracy(areaId: String) {
        val refreshed = reportRepository.refreshAccuracy(areaId)
        if (!refreshed) Log.w(LogTags.API, "Could not refresh accuracy for $areaId; showing the cached score")
        accuracyRefreshFailed.value = !refreshed
    }
}
