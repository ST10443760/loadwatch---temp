package com.example.loadwatch.core.designsystem

import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.loadwatch.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Shared formatting for schedule times. Instants from the API are shown in the device's local time zone
// and locale (FR-SCHED-02).

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
fun formatLocalTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(instant.atZone(zone))

@Composable
@ReadOnlyComposable
fun formatTimeRange(start: Instant, end: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    stringResource(R.string.time_range, formatLocalTime(start, zone), formatLocalTime(end, zone))

// "Today", "Tomorrow", or a short date such as "Fri 24 Oct"
@Composable
@ReadOnlyComposable
fun formatDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> DateTimeFormatter.ofPattern("EEE d MMM", currentLocale()).format(date)
}

// "4 minutes ago", "2 hours ago"
fun formatRelativeTime(instant: Instant, now: Instant): String =
    DateUtils.getRelativeTimeSpanString(instant.toEpochMilli(), now.toEpochMilli(), DateUtils.MINUTE_IN_MILLIS).toString()

// The countdown's visible text, e.g. "01:42:18", or "2d 03:10:05" beyond a day
fun formatCountdown(remaining: Duration): String {
    val totalSeconds = remaining.seconds.coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val clock = "%02d:%02d:%02d".format(Locale.ROOT, (totalSeconds % 86_400) / 3_600, (totalSeconds % 3_600) / 60, totalSeconds % 60)
    return if (days > 0) "${days}d $clock" else clock
}

// What screen readers announce for a countdown. Minute precision, so it is not re-read every second.
@Composable
@ReadOnlyComposable
fun describeDuration(remaining: Duration): String {
    val totalMinutes = remaining.toMinutes().coerceAtLeast(0)
    val hours = (totalMinutes / 60).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return when {
        hours == 0 && minutes == 0 -> stringResource(R.string.duration_under_a_minute)
        hours == 0 -> pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
        minutes == 0 -> pluralStringResource(R.plurals.duration_hours, hours, hours)
        else -> stringResource(
            R.string.duration_hours_minutes,
            pluralStringResource(R.plurals.duration_hours, hours, hours),
            pluralStringResource(R.plurals.duration_minutes, minutes, minutes),
        )
    }
}

@Composable
fun StageBadge(stage: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.stage_badge, stage),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
