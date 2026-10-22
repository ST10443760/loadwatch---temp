package com.example.loadwatch.feature.favourites

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.loadwatch.core.designsystem.LabelDialog
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.core.designsystem.describeDuration
import com.example.loadwatch.core.designsystem.formatCountdown
import com.example.loadwatch.core.designsystem.formatDayLabel
import com.example.loadwatch.core.designsystem.formatTimeRange
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.FavouriteLabel
import java.time.LocalDate
import java.time.ZoneId

// S08 Favourites and Compare: each saved area with its state, stage, next event and a live countdown on one
// screen. Removing a favourite never deletes the area or its schedule (FR-FAV-01 to 03).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesCompareScreen(
    onAddArea: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavouritesCompareViewModel = hiltViewModel(),
) {
    LogScreenLifecycle("S08")
    // Collected only while visible, so the countdown ticker stops when S08 is not on screen
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.favourites_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() },
        )

        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.cards.isEmpty() && state.refreshFailure != null && !state.isRefreshing -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                RecoveryState(
                    title = stringResource(state.refreshFailure!!.titleRes()),
                    message = stringResource(state.refreshFailure!!.messageRes()),
                    onRetry = viewModel::refresh,
                    secondaryActionLabel = stringResource(R.string.favourites_add_area),
                    onSecondaryAction = onAddArea,
                )
            }
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                CompareList(state = state, viewModel = viewModel)
            }
        }

        if (!state.isLoading) {
            OutlinedButton(
                onClick = onAddArea,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(if (state.cards.isEmpty()) R.string.favourites_add_area else R.string.favourites_add_another))
            }
        }
    }

    state.edit.editing?.let { favourite ->
        LabelDialog(
            title = stringResource(R.string.favourites_label_title, favourite.area.name),
            initialLabel = favourite.label,
            suggestions = listOf(stringResource(R.string.label_suggestion_home), stringResource(R.string.label_suggestion_work)),
            maxLength = FavouriteLabel.MAX_LENGTH,
            confirmLabel = stringResource(R.string.favourite_save),
            onConfirm = viewModel::saveLabel,
            onDismiss = viewModel::cancelEditing,
            isSaving = state.edit.isSaving,
        )
    }

    state.edit.confirmingRemoval?.let { favourite ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRemove,
            title = { Text(stringResource(R.string.favourites_remove_title, favourite.area.name)) },
            text = { Text(stringResource(R.string.favourites_remove_message)) },
            confirmButton = { TextButton(onClick = viewModel::confirmRemove) { Text(stringResource(R.string.favourites_remove)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRemove) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CompareList(state: CompareUiState, viewModel: FavouritesCompareViewModel) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.ofInstant(state.deviceNow, zone)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.edit.message?.let { message ->
            item(key = "message") {
                InlineMessage(
                    message = compareMessageText(message),
                    isError = message is CompareMessage.Failed || message == CompareMessage.LabelTooLong,
                    actionLabel = stringResource(R.string.action_close),
                    onAction = viewModel::dismissMessage,
                )
            }
        }
        if (state.refreshFailure != null && state.cards.isNotEmpty()) {
            item(key = "cached") {
                InlineMessage(
                    message = stringResource(
                        if (state.refreshFailure == DataFailure.OFFLINE) R.string.favourites_cached_offline else R.string.favourites_cached_error,
                    ),
                    isError = false,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::refresh,
                )
            }
        }
        if (state.cards.isEmpty()) {
            item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.favourites_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.favourites_empty_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(state.cards, key = { it.favourite.favouriteId }) { card ->
            FavouriteCard(
                card = card,
                today = today,
                zone = zone,
                onEditLabel = { viewModel.startEditing(card.favourite) },
                onRemove = { viewModel.requestRemove(card.favourite) },
            )
        }
    }
}

// Compact so three favourites fit a 6-inch screen. The state is written in words as well as coloured.
@Composable
private fun FavouriteCard(
    card: CompareCard,
    today: LocalDate,
    zone: ZoneId,
    onEditLabel: () -> Unit,
    onRemove: () -> Unit,
) {
    val countdown = card.countdown
    val status = LoadWatchTheme.statusColors
    val isOff = countdown?.state == SupplyState.OFF
    val container = when {
        countdown == null -> MaterialTheme.colorScheme.surfaceContainer
        isOff -> status.supplyOffContainer
        else -> status.supplyOnContainer
    }
    val content = when {
        countdown == null -> MaterialTheme.colorScheme.onSurface
        isOff -> status.onSupplyOffContainer
        else -> status.onSupplyOnContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = card.favourite.area.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = card.favourite.label.ifBlank { card.favourite.area.municipality },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CardMenu(areaName = card.favourite.area.name, onEditLabel = onEditLabel, onRemove = onRemove)
            }

            if (countdown == null) {
                Text(stringResource(R.string.favourites_schedule_not_loaded), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            val stage = (countdown.currentEvent ?: countdown.nextEvent)?.stage
            val stateText = stringResource(if (isOff) R.string.home_state_off else R.string.home_state_on)
            Text(
                text = if (stage != null && stage > 0) stringResource(R.string.favourites_state_stage, stateText, stage) else stateText,
                style = MaterialTheme.typography.titleSmall,
                color = if (isOff) status.supplyOff else status.supplyOn,
            )

            val remaining = countdown.remaining
            if (remaining != null) {
                val label = stringResource(if (isOff) R.string.home_label_power_returns_in else R.string.home_label_power_off_in)
                val description = stringResource(R.string.home_countdown_description, label, describeDuration(remaining))
                Row(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatCountdown(remaining),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            val next = countdown.nextEvent
            Text(
                text = if (next != null) {
                    stringResource(
                        R.string.favourites_next,
                        stringResource(
                            R.string.home_event_when,
                            formatDayLabel(LocalDate.ofInstant(next.startAt, zone), today),
                            formatTimeRange(next.startAt, next.endAt, zone),
                        ),
                    )
                } else {
                    stringResource(R.string.home_no_further_outage)
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CardMenu(areaName: String, onEditLabel: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.favourites_more_options, areaName))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favourites_edit_label)) },
                onClick = {
                    expanded = false
                    onEditLabel()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favourites_remove)) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun compareMessageText(message: CompareMessage): String = when (message) {
    is CompareMessage.Removed -> stringResource(R.string.favourite_removed, message.areaName)
    is CompareMessage.Relabelled -> stringResource(R.string.favourites_relabelled, message.areaName)
    CompareMessage.LabelTooLong -> stringResource(R.string.label_dialog_too_long, FavouriteLabel.MAX_LENGTH)
    is CompareMessage.Failed -> stringResource(
        when (message.failure) {
            DataFailure.OFFLINE -> R.string.favourite_failed_offline
            DataFailure.NOT_SIGNED_IN -> R.string.error_not_signed_in_message
            DataFailure.SERVER, DataFailure.NOT_FOUND -> R.string.favourite_failed
        },
    )
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
