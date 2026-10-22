package com.example.loadwatch.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.core.designsystem.StageBadge
import com.example.loadwatch.core.designsystem.formatDayLabel
import com.example.loadwatch.core.designsystem.formatRelativeTime
import com.example.loadwatch.core.designsystem.formatTimeRange
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.schedule.ScheduleDay
import java.time.LocalDate
import java.time.ZoneId

// S06 Schedule: the upcoming window as a dated timeline with stage badges, a range selector, and a stale badge
// when the cached data is more than six hours old (FR-SCHED-01, 02, 04).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S06")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.schedule_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            (uiState as? ScheduleUiState.Ready)?.let { ready ->
                Text(
                    text = ready.area.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RangeSelector(selected = range, onSelect = viewModel::selectRange)
        }

        val state = uiState
        when (state) {
            ScheduleUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ScheduleUiState.NoArea -> Text(
                text = stringResource(R.string.home_no_area_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            is ScheduleUiState.Unavailable -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    RecoveryState(
                        title = stringResource(state.failure.titleRes()),
                        message = stringResource(state.failure.messageRes()),
                        onRetry = viewModel::refresh,
                    )
                }
            }
            is ScheduleUiState.Ready -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Timeline(state = state, onRetry = viewModel::refresh)
            }
        }
    }
}

@Composable
private fun RangeSelector(selected: ScheduleRange, onSelect: (ScheduleRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ScheduleRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ScheduleRange.entries.size),
            ) {
                Text(pluralStringResource(R.plurals.schedule_range_days, range.days, range.days))
            }
        }
    }
}

@Composable
private fun Timeline(state: ScheduleUiState.Ready, onRetry: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val updated = formatRelativeTime(state.lastRefreshedAt, state.deviceNow)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "freshness") {
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
                // Stale-data badge (FR-SCHED-04)
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
        items(state.days, key = { it.date.toEpochDay() }) { day ->
            DaySection(day = day, today = state.today, zone = zone)
        }
    }
}

@Composable
private fun DaySection(day: ScheduleDay, today: LocalDate, zone: ZoneId) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = formatDayLabel(day.date, today),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        when {
            !day.isLoaded -> Text(
                text = stringResource(R.string.schedule_not_loaded),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            day.events.isEmpty() -> Text(
                text = stringResource(R.string.schedule_no_outage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> day.events.forEach { event ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StageBadge(stage = event.stage)
                    Text(
                        text = formatTimeRange(event.startAt, event.endAt, zone),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
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
