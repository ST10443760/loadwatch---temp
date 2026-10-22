package com.example.loadwatch.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.common.SyncStatus
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.core.designsystem.accuracyText
import com.example.loadwatch.core.designsystem.formatDayLabel
import com.example.loadwatch.core.designsystem.formatLocalTime
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.OutageReport
import java.time.LocalDate
import java.time.ZoneId

// S07 Community Confirmation: two structured actions, the submission's lifecycle, and the area's accuracy score
// so the user sees the effect of what they reported (FR-REP-01 to 04).
@Composable
fun CommunityConfirmationScreen(
    onChooseArea: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityConfirmationViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S07")
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val area = state.area
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        area == null -> RecoveryState(
            title = stringResource(R.string.home_no_area_title),
            message = stringResource(R.string.home_no_area_message),
            retryLabel = stringResource(R.string.home_choose_area),
            onRetry = onChooseArea,
            modifier = modifier,
        )
        else -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.report_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.report_area, area.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ReportButtons(enabled = !state.isSending, onReport = viewModel::report)

            StatusCard(state = state, onRetry = viewModel::retry, onRetryUnsent = viewModel::retryUnsent)

            AccuracyCard(state = state, onRetry = viewModel::refreshAccuracy)

            RecentReports(reports = state.recentReports)
        }
    }
}

// Red for off and green for back on, always labelled in words. Disabled while a report is in flight, so a
// double tap cannot send twice.
@Composable
private fun ReportButtons(enabled: Boolean, onReport: (OutageEventType) -> Unit) {
    val status = LoadWatchTheme.statusColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onReport(OutageEventType.POWER_OFF) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = status.supplyOff, contentColor = status.onSupplyOff),
        ) { Text(stringResource(R.string.report_power_off)) }
        Button(
            onClick = { onReport(OutageEventType.POWER_BACK_ON) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = status.supplyOn, contentColor = status.onSupplyOn),
        ) { Text(stringResource(R.string.report_power_back)) }
    }
}

@Composable
private fun StatusCard(state: ConfirmationUiState, onRetry: () -> Unit, onRetryUnsent: () -> Unit) {
    val submission = state.submission
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (submission) {
            SubmissionStatus.Idle -> Unit
            is SubmissionStatus.Sending -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.report_status_sending), style = MaterialTheme.typography.bodyMedium)
            }
            is SubmissionStatus.Accepted -> InlineMessage(
                message = stringResource(R.string.report_status_accepted, stringResource(submission.eventType.labelRes())),
                isError = false,
            )
            is SubmissionStatus.AlreadyReported -> InlineMessage(
                message = stringResource(R.string.report_status_already_reported),
                isError = false,
            )
            is SubmissionStatus.SavedOffline -> InlineMessage(
                message = stringResource(R.string.report_status_saved_offline),
                isError = false,
                actionLabel = stringResource(R.string.report_retry_sending),
                onAction = onRetryUnsent,
            )
            is SubmissionStatus.Rejected -> InlineMessage(
                message = stringResource(
                    if ("reportedAt" in submission.fields) R.string.report_status_rejected_clock else R.string.report_status_rejected,
                ),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            is SubmissionStatus.NotSignedIn -> InlineMessage(
                message = stringResource(R.string.report_status_not_signed_in),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            is SubmissionStatus.Failed -> InlineMessage(
                message = stringResource(
                    if (submission.failure == DataFailure.OFFLINE) R.string.report_status_saved_offline else R.string.report_status_failed,
                ),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
        }

        // Reports waiting on the phone can always be sent again by hand; automatic sync is Final POE scope
        if (state.unsentCount > 0 && !state.isSending && submission !is SubmissionStatus.SavedOffline) {
            OutlinedButton(onClick = onRetryUnsent, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.report_retry_sending_count, state.unsentCount))
            }
        }
    }
}

@Composable
private fun AccuracyCard(state: ConfirmationUiState, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.accuracy_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = accuracyText(state.accuracy?.score, state.accuracy?.sampleSize), style = MaterialTheme.typography.bodyLarge)
            if (state.accuracyRefreshFailed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.report_accuracy_cached),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

@Composable
private fun RecentReports(reports: List<OutageReport>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.report_recent_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (reports.isEmpty()) {
            Text(
                text = stringResource(R.string.report_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reports.forEach { report ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(report.eventType.labelRes()), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(
                            R.string.home_target_at,
                            formatDayLabel(LocalDate.ofInstant(report.reportedAt, zone), today),
                            formatLocalTime(report.reportedAt, zone),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(report.syncStatus.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (report.syncStatus == SyncStatus.REJECTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
        }
    }
}

private fun OutageEventType.labelRes(): Int = when (this) {
    OutageEventType.POWER_OFF -> R.string.report_event_power_off
    OutageEventType.POWER_BACK_ON -> R.string.report_event_power_back
}

private fun SyncStatus.labelRes(): Int = when (this) {
    SyncStatus.PENDING -> R.string.report_sync_pending
    SyncStatus.RETRYING -> R.string.report_sync_retrying
    SyncStatus.SYNCED -> R.string.report_sync_synced
    SyncStatus.REJECTED -> R.string.report_sync_rejected
}
