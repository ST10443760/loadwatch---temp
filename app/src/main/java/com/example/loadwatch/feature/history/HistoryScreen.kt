package com.example.loadwatch.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.core.common.OutageEventType
import com.example.loadwatch.core.designsystem.EmptyState
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.core.designsystem.LoadingState
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.core.designsystem.Spacing
import com.example.loadwatch.core.designsystem.StageBadge
import com.example.loadwatch.core.designsystem.formatLocalTime
import com.example.loadwatch.core.designsystem.formatRelativeTime
import com.example.loadwatch.core.designsystem.formatTimeRange
import com.example.loadwatch.data.history.HistoryCalendar
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.HistoryDay
import com.example.loadwatch.data.model.HistoryOutage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// S10 Outage History (user-defined feature 5): the selected area's past outages and community reports, as a month
// calendar shaded by hours without power or as a list of days. Works offline for any month seen before.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onChooseArea: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S10")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Header(
            areaName = (uiState as? HistoryUiState.Ready)?.area?.name,
            onBack = onBack,
        )

        when (val state = uiState) {
            HistoryUiState.Loading -> LoadingState(message = stringResource(R.string.history_loading))
            HistoryUiState.NoArea -> EmptyState(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.home_no_area_title),
                message = stringResource(R.string.home_no_area_message),
                actionLabel = stringResource(R.string.home_choose_area),
                onAction = onChooseArea,
            )
            is HistoryUiState.Unavailable -> {
                MonthControls(
                    month = state.month,
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    view = view,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onSelectView = viewModel::selectView,
                )
                PullToRefreshBox(
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
            }
            is HistoryUiState.Ready -> {
                MonthControls(
                    month = state.month,
                    canGoBack = state.canGoBack,
                    canGoForward = state.canGoForward,
                    view = view,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onSelectView = viewModel::selectView,
                )
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HistoryContent(state = state, view = view, onSelectDay = viewModel::selectDay, onRetry = viewModel::refresh)
                }
            }
        }
    }
}

/** Top bar of S10 Outage history with the back button and the area's name. */
@Composable
private fun Header(areaName: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.lg, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (areaName != null) {
                Text(
                    text = areaName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Previous and next month buttons and the Calendar or List switch. The arrows stop at the oldest month kept
 * and at the current month.
 */
@Composable
private fun MonthControls(
    month: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    view: HistoryView,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectView: (HistoryView) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.history_previous_month))
            }
            Text(
                text = formatMonth(month),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.history_next_month))
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            HistoryView.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == view,
                    onClick = { onSelectView(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = HistoryView.entries.size),
                ) {
                    Text(stringResource(if (option == HistoryView.CALENDAR) R.string.history_view_calendar else R.string.history_view_list))
                }
            }
        }
    }
}

/** Shows the selected month as a calendar or a list, with the summary above it. */
@Composable
private fun HistoryContent(
    state: HistoryUiState.Ready,
    view: HistoryView,
    onSelectDay: (LocalDate) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "summary") { Summary(state = state, onRetry = onRetry) }

        when {
            state.isEmpty -> item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty_message, state.area.name),
                )
            }
            view == HistoryView.CALENDAR -> {
                item(key = "calendar") { MonthGrid(state = state, onSelectDay = onSelectDay) }
                item(key = "legend") {
                    Text(
                        text = stringResource(R.string.history_legend),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "selected") {
                    val day = state.selectedDay
                    when {
                        day != null -> DayCard(day = day, today = HistoryCalendar.dateOf(state.deviceNow))
                        state.selectedDate != null -> Text(
                            text = stringResource(R.string.history_day_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Text(
                            text = stringResource(R.string.history_pick_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> items(state.days, key = { it.date.toEpochDay() }) { day ->
                DayCard(day = day, today = HistoryCalendar.dateOf(state.deviceNow))
            }
        }
    }
}

/**
 * The month's outage count, hours without power, and when it was last updated, with a retry when the refresh
 * failed.
 */
@Composable
private fun Summary(state: HistoryUiState.Ready, onRetry: () -> Unit) {
    val updated = formatRelativeTime(state.fetchedAt, state.deviceNow)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (state.refreshFailure != null) {
            InlineMessage(
                message = stringResource(
                    if (state.refreshFailure == DataFailure.OFFLINE) R.string.history_cached_offline else R.string.history_cached_error,
                    updated,
                ),
                isError = false,
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
        }
        if (!state.isEmpty) {
            val hours = (state.minutesWithoutPower / 60).toInt()
            Text(
                text = stringResource(
                    R.string.history_month_summary,
                    pluralStringResource(R.plurals.history_month_outages, state.outageCount, state.outageCount),
                    pluralStringResource(R.plurals.history_month_hours, hours, hours),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (state.refreshFailure == null) {
            Text(
                text = stringResource(R.string.history_updated, updated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Month calendar, Monday first. Each day is shaded by hours without power and has a dot when the community
 * reported on it.
 */
@Composable
private fun MonthGrid(state: HistoryUiState.Ready, onSelectDay: (LocalDate) -> Unit) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            weekDays().forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { cell ->
                    if (cell == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            cell = cell,
                            isSelected = cell.date == state.selectedDate,
                            onClick = { onSelectDay(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

// A day shaded by hours without power, with a dot when the community reported that day. Colour is never the only
// signal: the label read out says how many outages and whether there were reports.
@Composable
private fun DayCell(cell: CalendarCell, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val status = LoadWatchTheme.statusColors
    val shape = MaterialTheme.shapes.small
    val background = when (cell.level) {
        0 -> MaterialTheme.colorScheme.surfaceContainer
        1 -> status.supplyOffContainer.copy(alpha = 0.4f)
        2 -> status.supplyOffContainer.copy(alpha = 0.7f)
        else -> status.supplyOffContainer
    }
    val content = if (cell.level == 3) status.onSupplyOffContainer else MaterialTheme.colorScheme.onSurface
    val label = describeDay(cell)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(enabled = !cell.isFuture, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (cell.isFuture) content.copy(alpha = 0.38f) else content,
        )
        if (cell.hasReports) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xs)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** The outages and community reports for one day. */
@Composable
private fun DayCard(day: HistoryDay, today: LocalDate) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = formatLongDate(day.date, today),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            day.outages.forEach { outage -> OutageRow(outage) }
            if (day.reports.isNotEmpty()) {
                if (day.outages.isNotEmpty()) HorizontalDivider()
                Text(
                    text = stringResource(R.string.history_reports_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                day.reports.forEach { report ->
                    Text(
                        text = stringResource(
                            if (report.eventType == OutageEventType.POWER_OFF) R.string.history_report_power_off else R.string.history_report_power_back,
                            formatLocalTime(report.reportedAt, HistoryCalendar.ZONE),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** One past outage with its stage, time range and how many community reports fall inside it. */
@Composable
private fun OutageRow(outage: HistoryOutage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        StageBadge(stage = outage.stage)
        Column(Modifier.weight(1f)) {
            Text(
                text = formatTimeRange(outage.startAt, outage.endAt, HistoryCalendar.ZONE),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (outage.reportCount == 0) {
                    stringResource(R.string.history_outage_no_reports)
                } else {
                    pluralStringResource(R.plurals.history_outage_reports, outage.reportCount, outage.reportCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** TalkBack description of a calendar day, so the shading and the report dot are read out. */
@Composable
private fun describeDay(cell: CalendarCell): String {
    val date = formatLongDate(cell.date, today = null)
    return when {
        cell.outageCount == 0 && cell.hasReports -> stringResource(R.string.history_day_none_reports_cd, date)
        cell.outageCount == 0 -> stringResource(R.string.history_day_none_cd, date)
        cell.hasReports -> pluralStringResource(R.plurals.history_day_outages_reports_cd, cell.outageCount, cell.outageCount, date)
        else -> pluralStringResource(R.plurals.history_day_outages_cd, cell.outageCount, cell.outageCount, date)
    }
}

/** The app's current locale, which follows the language chosen in Settings. */
@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

// Monday first, as South African calendars are laid out
private fun weekDays(): List<DayOfWeek> = (0L..6L).map { DayOfWeek.MONDAY.plus(it) }

/** Formats a month heading, such as November 2026, in the app's language. */
@Composable
@ReadOnlyComposable
private fun formatMonth(month: YearMonth): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", currentLocale()).format(month)

// "Today", or a full date such as "Friday 7 November"
@Composable
@ReadOnlyComposable
private fun formatLongDate(date: LocalDate, today: LocalDate?): String =
    if (date == today) stringResource(R.string.day_today) else DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale()).format(date)

private fun DataFailure.titleRes(): Int = when (this) {
    DataFailure.OFFLINE -> R.string.error_offline_title
    DataFailure.SERVER -> R.string.error_server_title
    DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_title
    DataFailure.NOT_FOUND -> R.string.error_not_found_title
}

private fun DataFailure.messageRes(): Int = when (this) {
    DataFailure.OFFLINE -> R.string.history_unavailable_offline
    DataFailure.SERVER -> R.string.error_server_message
    DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_message
    DataFailure.NOT_FOUND -> R.string.error_not_found_message
}
