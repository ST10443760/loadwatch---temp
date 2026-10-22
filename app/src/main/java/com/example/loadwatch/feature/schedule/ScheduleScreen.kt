package com.example.loadwatch.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S06 Schedule: the upcoming window as a dated timeline with stage badges and a stale-data badge
// (FR-SCHED-01, 02, 04).
@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        screenId = stringResource(R.string.schedule_screen_id),
        title = stringResource(R.string.schedule_title),
        purpose = stringResource(R.string.schedule_purpose),
        modifier = modifier,
    )
}
