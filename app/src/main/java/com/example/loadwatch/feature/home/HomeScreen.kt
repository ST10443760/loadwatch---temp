package com.example.loadwatch.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderAction
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S04 Home Dashboard, the hub: supply state, live countdown, next event and accuracy badge, with entry points
// to the area selector and community confirmation (FR-SCHED-03, FR-COUNT-01 to 03). Still a placeholder apart
// from the active area's name.
@Composable
fun HomeScreen(
    onChangeArea: () -> Unit,
    onConfirmOutage: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val activeArea by viewModel.activeArea.collectAsStateWithLifecycle()

    PlaceholderScreen(
        screenId = stringResource(R.string.home_screen_id),
        title = activeArea?.name ?: stringResource(R.string.home_title),
        purpose = stringResource(R.string.home_purpose),
        modifier = modifier,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.home_confirm_outage), onConfirmOutage, isPrimary = true),
            PlaceholderAction(stringResource(R.string.home_change_area), onChangeArea),
        ),
    )
}
