package com.example.loadwatch.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.common.SupplyState
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.core.designsystem.StageBadge
import com.example.loadwatch.core.designsystem.describeDuration
import com.example.loadwatch.core.designsystem.formatCountdown
import com.example.loadwatch.core.designsystem.formatDayLabel
import com.example.loadwatch.core.designsystem.formatLocalTime
import com.example.loadwatch.core.designsystem.formatRelativeTime
import com.example.loadwatch.core.designsystem.formatTimeRange
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.ScheduleEvent
import com.example.loadwatch.data.repository.AccuracyScore
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

// S04 Home Dashboard, the hub: area, supply state in words, the live countdown as the largest element, the next
// event, the current stage, the area's accuracy badge and when the data was last updated, all on one screen
// without scrolling (FR-SCHED-03, FR-COUNT-01 to 03, NFR-06).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChangeArea: () -> Unit,
    onConfirmOutage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S04")
    // Collected only while Home is at least started, so the one-second ticker stops when it is not visible
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        HomeUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        HomeUiState.NoArea -> RecoveryState(
            title = stringResource(R.string.home_no_area_title),
            message = stringResource(R.string.home_no_area_message),
            retryLabel = stringResource(R.string.home_choose_area),
            onRetry = onChangeArea,
            modifier = modifier,
        )
        is HomeUiState.Unavailable -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                RecoveryState(
                    title = stringResource(state.failure.titleRes()),
                    message = stringResource(state.failure.messageRes()),
                    onRetry = viewModel::refresh,
                    secondaryActionLabel = stringResource(R.string.home_change_area),
                    onSecondaryAction = onChangeArea,
                )
            }
        }
        is HomeUiState.Ready -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            HomeContent(state = state, onChangeArea = onChangeArea, onReport = onConfirmOutage, onRetry = viewModel::refresh)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    onChangeArea: () -> Unit,
    onReport: () -> Unit,
    onRetry: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.ofInstant(state.deviceNow, zone)

    // Scrollable only so pull-to-refresh works; the content fits a 6-inch screen without scrolling
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.area.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = state.area.municipality,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChangeArea) { Text(stringResource(R.string.home_change_area)) }
        }

        SupplyStatusCard(state = state, today = today, zone = zone)

        NextEventCard(
            current = state.countdown.currentEvent,
            next = state.countdown.nextEvent,
            today = today,
            zone = zone,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StageCard(stage = state.currentStage, modifier = Modifier.weight(1f))
            AccuracyCard(accuracy = state.accuracy, modifier = Modifier.weight(1f))
        }

        FreshnessLine(state = state, onRetry = onRetry)

        Button(onClick = onReport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_report))
        }
    }
}

// Green when power is on, red when it is off. The state is always written in words too, so colour is never the
// only carrier of meaning (NFR-07).
@Composable
private fun SupplyStatusCard(state: HomeUiState.Ready, today: LocalDate, zone: ZoneId) {
    val countdown = state.countdown
    val status = LoadWatchTheme.statusColors
    val isOff = countdown.state == SupplyState.OFF
    val container = if (isOff) status.supplyOffContainer else status.supplyOnContainer
    val content = if (isOff) status.onSupplyOffContainer else status.onSupplyOnContainer
    val accent = if (isOff) status.supplyOff else status.supplyOn

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(if (isOff) R.string.home_state_off else R.string.home_state_on),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                modifier = Modifier.semantics { heading() },
            )

            val remaining = countdown.remaining
            val target = countdown.target
            if (remaining != null && target != null) {
                val label = stringResource(if (isOff) R.string.home_label_power_returns_in else R.string.home_label_power_off_in)
                val description = stringResource(R.string.home_countdown_description, label, describeDuration(remaining))
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatCountdown(remaining),
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                )
                Text(
                    text = stringResource(
                        R.string.home_target_at,
                        formatDayLabel(LocalDate.ofInstant(target, zone), today),
                        formatLocalTime(target, zone),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(text = stringResource(R.string.home_no_upcoming_outage), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun NextEventCard(current: ScheduleEvent?, next: ScheduleEvent?, today: LocalDate, zone: ZoneId) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (current != null) {
                EventLine(
                    title = stringResource(R.string.home_current_outage),
                    event = current,
                    today = today,
                    zone = zone,
                )
            }
            if (next != null) {
                EventLine(title = stringResource(R.string.home_next_outage), event = next, today = today, zone = zone)
            } else {
                Text(
                    text = stringResource(R.string.home_no_further_outage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventLine(title: String, event: ScheduleEvent, today: LocalDate, zone: ZoneId) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(
                    R.string.home_event_when,
                    formatDayLabel(LocalDate.ofInstant(event.startAt, zone), today),
                    formatTimeRange(event.startAt, event.endAt, zone),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (event.stage > 0) StageBadge(stage = event.stage)
    }
}

@Composable
private fun StageCard(stage: Int?, modifier: Modifier = Modifier) {
    SmallInfoCard(
        title = stringResource(R.string.home_current_stage),
        value = when {
            stage == null -> stringResource(R.string.home_stage_unknown)
            stage == 0 -> stringResource(R.string.home_stage_none)
            else -> stringResource(R.string.stage_badge, stage)
        },
        modifier = modifier,
    )
}

// "86% schedule match from 42 reports", or "No reports yet"
@Composable
private fun AccuracyCard(accuracy: AccuracyScore?, modifier: Modifier = Modifier) {
    SmallInfoCard(
        title = stringResource(R.string.home_accuracy_title),
        value = when {
            accuracy == null -> stringResource(R.string.home_accuracy_unknown)
            accuracy.sampleSize == 0 -> stringResource(R.string.home_accuracy_no_reports)
            else -> pluralStringResource(
                R.plurals.home_accuracy_match,
                accuracy.sampleSize,
                (accuracy.score * 100).roundToInt(),
                accuracy.sampleSize,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun SmallInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Every screen states its data freshness: last updated, plus a cached or stale notice when relevant
@Composable
private fun FreshnessLine(state: HomeUiState.Ready, onRetry: () -> Unit) {
    val updated = formatRelativeTime(state.lastRefreshedAt, state.deviceNow)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            state.refreshFailure != null -> InlineMessage(
                message = stringResource(
                    if (state.refreshFailure == DataFailure.OFFLINE) R.string.schedule_cached_offline else R.string.schedule_cached_error,
                    updated,
                ),
                isError = state.isStale,
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            state.isStale -> InlineMessage(
                message = stringResource(R.string.schedule_stale, updated),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            else -> Text(
                text = stringResource(R.string.schedule_updated, updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DataFailure.titleRes(): Int = when (this) {
    DataFailure.OFFLINE -> R.string.error_offline_title
    DataFailure.SERVER -> R.string.error_server_title
    DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_title
    DataFailure.NOT_FOUND -> R.string.error_not_found_title
}

private fun DataFailure.messageRes(): Int = when (this) {
    DataFailure.OFFLINE -> R.string.error_offline_message
    DataFailure.SERVER -> R.string.error_server_message
    DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_message
    DataFailure.NOT_FOUND -> R.string.error_not_found_message
}
