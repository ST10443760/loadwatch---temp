package com.example.loadwatch.feature.area

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle
import com.example.loadwatch.data.model.Area

// S05 Area Selector: switches the active area from search or recents. Opened from Home and returns there once
// the switch is saved. Favourite chips arrive with the favourites feature.
@Composable
fun AreaSelectorScreen(
    onAreaSelected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AreaSelectorViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S05")
    val query by viewModel.search.query.collectAsStateWithLifecycle()
    val searchState by viewModel.search.state.collectAsStateWithLifecycle()
    val choiceState by viewModel.choice.state.collectAsStateWithLifecycle()
    val currentArea by viewModel.currentArea.collectAsStateWithLifecycle()
    val recentAreas by viewModel.recentAreas.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.finished.collect { onAreaSelected() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.area_selector_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        currentArea?.let { area ->
            Text(
                text = stringResource(R.string.area_selector_current, area.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AreaSearchField(query = query, onQueryChange = viewModel.search::onQueryChange)

        if (choiceState.isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val problem = choiceState.problem
        if (problem != null) {
            AreaSaveProblem(
                problem = problem,
                onRetry = viewModel.choice::retry,
                onContinue = viewModel.choice::continueOnThisDevice,
                modifier = Modifier.weight(1f),
            )
        } else {
            AreaSearchResults(
                state = searchState,
                mode = AreaRowMode.SWITCH,
                enabled = !choiceState.isSaving,
                onAreaClick = viewModel::switchTo,
                onRetry = viewModel.search::retry,
                modifier = Modifier.weight(1f),
                idleContent = {
                    RecentAreas(
                        areas = recentAreas,
                        enabled = !choiceState.isSaving,
                        onAreaClick = viewModel::switchTo,
                    )
                },
            )
        }
    }
}

@Composable
private fun RecentAreas(
    areas: List<Area>,
    enabled: Boolean,
    onAreaClick: (Area) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.area_selector_recent),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (areas.isEmpty()) {
            Text(
                text = stringResource(R.string.area_selector_no_recents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(areas, key = { it.areaId }) { area ->
                    AreaRow(area = area, mode = AreaRowMode.SWITCH, enabled = enabled, onClick = { onAreaClick(area) })
                    HorizontalDivider()
                }
            }
        }
    }
}
