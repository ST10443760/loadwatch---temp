package com.example.loadwatch.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S07 Community Confirmation: power is off, or power is back, with the submission status and its effect
// on the area's accuracy score (FR-REP-01 to 05).
@Composable
fun CommunityConfirmationScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        screenId = stringResource(R.string.report_screen_id),
        title = stringResource(R.string.report_title),
        purpose = stringResource(R.string.report_purpose),
        modifier = modifier,
    )
}
