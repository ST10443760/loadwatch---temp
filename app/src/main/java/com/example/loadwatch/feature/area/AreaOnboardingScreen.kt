package com.example.loadwatch.feature.area

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle

// S03 Area Onboarding: a search-first list that resolves a suburb to an area with its municipality and supplier
// visible, then saves it as the default area on the device and to the API (FR-AREA-01 to 03).
@Composable
fun AreaOnboardingScreen(
    onAreaConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AreaOnboardingViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S03")
    val query by viewModel.search.query.collectAsStateWithLifecycle()
    val searchState by viewModel.search.state.collectAsStateWithLifecycle()
    val selectedArea by viewModel.selectedArea.collectAsStateWithLifecycle()
    val choiceState by viewModel.choice.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.choice.done.collect { onAreaConfirmed() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.onboarding_purpose),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AreaSearchField(query = query, onQueryChange = viewModel.search::onQueryChange)

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
                mode = AreaRowMode.SELECT,
                selectedAreaId = selectedArea?.areaId,
                enabled = !choiceState.isSaving,
                onAreaClick = viewModel::select,
                onRetry = viewModel.search::retry,
                modifier = Modifier.weight(1f),
            )
        }

        selectedArea?.let { area ->
            Text(
                text = stringResource(R.string.onboarding_selected, area.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Button(
            onClick = viewModel::confirm,
            enabled = selectedArea != null && !choiceState.isSaving && problem == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (choiceState.isSaving) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.onboarding_saving))
                }
            } else {
                Text(stringResource(R.string.onboarding_confirm_area))
            }
        }
    }
}
