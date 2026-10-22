package com.example.loadwatch.feature.area

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.LabelDialog
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Favourite
import com.example.loadwatch.data.model.FavouriteLabel

// S05 Area Selector: switches the active area from favourites, search or recents, and saves favourites with a
// star on each result. Opened from S08 in add-favourite mode, a tap saves the area as a favourite instead.
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
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val favouriteEdit by viewModel.favouriteEdit.collectAsStateWithLifecycle()
    val addMode = viewModel.addFavouriteMode

    LaunchedEffect(viewModel) {
        viewModel.finished.collect { onAreaSelected() }
    }

    val favouriteIds = favourites.map { it.area.areaId }.toSet()
    val star: @Composable (Area) -> Unit = { area ->
        FavouriteToggle(isFavourite = area.areaId in favouriteIds, areaName = area.name) { viewModel.onToggleFavourite(area) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (addMode) R.string.area_selector_add_title else R.string.area_selector_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (addMode) {
            Text(
                text = stringResource(R.string.area_selector_add_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            currentArea?.let { area ->
                Text(
                    text = stringResource(R.string.area_selector_current, area.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FavouriteChips(favourites = favourites, enabled = !choiceState.isSaving, onClick = { viewModel.onAreaTapped(it.area) })
        }

        favouriteEdit.message?.let { message ->
            InlineMessage(
                message = favouriteMessageText(message),
                isError = message is FavouriteMessage.Failed || message == FavouriteMessage.LabelTooLong,
                actionLabel = stringResource(R.string.action_close),
                onAction = viewModel::dismissMessage,
            )
        }

        AreaSearchField(query = query, onQueryChange = viewModel.search::onQueryChange)

        if (choiceState.isSaving || favouriteEdit.isSaving) {
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
                onAreaClick = viewModel::onAreaTapped,
                onRetry = viewModel.search::retry,
                modifier = Modifier.weight(1f),
                rowTrailing = if (addMode) null else star,
                idleContent = {
                    RecentAreas(
                        areas = recentAreas,
                        enabled = !choiceState.isSaving,
                        onAreaClick = viewModel::onAreaTapped,
                        trailing = if (addMode) null else star,
                    )
                },
            )
        }
    }

    favouriteEdit.pendingArea?.let { area ->
        LabelDialog(
            title = stringResource(R.string.favourite_add_title, area.name),
            initialLabel = "",
            suggestions = listOf(stringResource(R.string.label_suggestion_home), stringResource(R.string.label_suggestion_work)),
            maxLength = FavouriteLabel.MAX_LENGTH,
            confirmLabel = stringResource(R.string.favourite_save),
            onConfirm = viewModel::confirmFavourite,
            onDismiss = viewModel::dismissLabelDialog,
            isSaving = favouriteEdit.isSaving,
            errorMessage = if (favouriteEdit.message == FavouriteMessage.LabelTooLong) {
                stringResource(R.string.label_dialog_too_long, FavouriteLabel.MAX_LENGTH)
            } else {
                null
            },
        )
    }
}

// Favourite chips switch to a saved area in one tap
@Composable
private fun FavouriteChips(favourites: List<Favourite>, enabled: Boolean, onClick: (Favourite) -> Unit) {
    if (favourites.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.area_selector_favourites),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favourites, key = { it.favouriteId }) { favourite ->
                AssistChip(
                    onClick = { onClick(favourite) },
                    enabled = enabled,
                    label = {
                        Text(
                            if (favourite.label.isBlank()) {
                                favourite.area.name
                            } else {
                                stringResource(R.string.favourite_chip, favourite.label, favourite.area.name)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FavouriteToggle(isFavourite: Boolean, areaName: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
            contentDescription = stringResource(
                if (isFavourite) R.string.favourite_remove_description else R.string.favourite_add_description,
                areaName,
            ),
            tint = if (isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentAreas(
    areas: List<Area>,
    enabled: Boolean,
    onAreaClick: (Area) -> Unit,
    trailing: (@Composable (Area) -> Unit)?,
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
                    AreaRow(
                        area = area,
                        mode = AreaRowMode.SWITCH,
                        enabled = enabled,
                        onClick = { onAreaClick(area) },
                        trailing = trailing?.let { content -> { content(area) } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun favouriteMessageText(message: FavouriteMessage): String = when (message) {
    is FavouriteMessage.Added -> stringResource(R.string.favourite_added, message.areaName)
    FavouriteMessage.AlreadyFavourite -> stringResource(R.string.favourite_already)
    is FavouriteMessage.Removed -> stringResource(R.string.favourite_removed, message.areaName)
    FavouriteMessage.LabelTooLong -> stringResource(R.string.label_dialog_too_long, FavouriteLabel.MAX_LENGTH)
    is FavouriteMessage.Failed -> stringResource(
        when (message.failure) {
            DataFailure.OFFLINE -> R.string.favourite_failed_offline
            DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_message
            DataFailure.SERVER, DataFailure.NOT_FOUND -> R.string.favourite_failed
        },
    )
}
