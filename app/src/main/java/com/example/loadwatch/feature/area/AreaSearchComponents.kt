package com.example.loadwatch.feature.area

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.InlineMessage
import com.example.loadwatch.core.designsystem.RecoveryState
import com.example.loadwatch.data.model.Area
import com.example.loadwatch.data.model.DataFailure
import com.example.loadwatch.data.model.Supplier
import com.example.loadwatch.data.repository.SaveSettingsResult

@Composable
fun AreaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.area_search_label)) },
        supportingText = { Text(stringResource(R.string.area_search_hint, AreaSearch.MIN_QUERY_LENGTH)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.area_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
    )
}

// How a result row responds to a tap: a radio choice confirmed later (S03), or an immediate switch (S05)
enum class AreaRowMode { SELECT, SWITCH }

@Composable
fun AreaRow(
    area: Area,
    mode: AreaRowMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = when (mode) {
        AreaRowMode.SELECT -> Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        AreaRowMode.SWITCH -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    ListItem(
        modifier = modifier.then(interaction),
        headlineContent = { Text(area.name) },
        // Municipality and supplier make similarly named suburbs distinguishable without opening either (FR-AREA-02)
        supportingContent = {
            Text(stringResource(R.string.area_row_details, area.municipality, stringResource(area.supplier.labelRes())))
        },
        leadingContent = if (mode == AreaRowMode.SELECT) {
            { RadioButton(selected = selected, onClick = null, enabled = enabled) }
        } else {
            null
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// Everything below the search field: hints, progress, results, and labelled recovery states
@Composable
fun AreaSearchResults(
    state: AreaSearchState,
    onAreaClick: (Area) -> Unit,
    onRetry: () -> Unit,
    mode: AreaRowMode,
    modifier: Modifier = Modifier,
    selectedAreaId: String? = null,
    enabled: Boolean = true,
    idleContent: @Composable () -> Unit = {},
    // Extra content at the end of each result row, such as a favourite toggle
    rowTrailing: (@Composable (Area) -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when (state) {
            AreaSearchState.Idle -> idleContent()
            AreaSearchState.InvalidCharacters ->
                InlineMessage(message = stringResource(R.string.area_search_invalid))
            AreaSearchState.Searching ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is AreaSearchState.NoMatches ->
                Text(
                    text = stringResource(R.string.area_search_no_matches, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            is AreaSearchState.Failed ->
                RecoveryState(
                    title = stringResource(state.failure.titleRes()),
                    message = stringResource(state.failure.messageRes()),
                    onRetry = onRetry,
                )
            is AreaSearchState.Results ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.fromCache) {
                        InlineMessage(
                            message = stringResource(R.string.area_search_cached_notice),
                            isError = false,
                            actionLabel = stringResource(R.string.action_retry),
                            onAction = onRetry,
                        )
                    }
                    LazyColumn {
                        items(state.areas, key = { it.areaId }) { area ->
                            AreaRow(
                                area = area,
                                mode = mode,
                                selected = area.areaId == selectedAreaId,
                                enabled = enabled,
                                onClick = { onAreaClick(area) },
                                trailing = rowTrailing?.let { content -> { content(area) } },
                            )
                            HorizontalDivider()
                        }
                    }
                }
        }
    }
}

// Shown after a save attempt that did not fully succeed, always with a way forward
@Composable
fun AreaSaveProblem(
    problem: SaveSettingsResult,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (problem) {
        SaveSettingsResult.Saved -> Unit
        is SaveSettingsResult.SavedOnDeviceOnly ->
            RecoveryState(
                title = stringResource(R.string.area_save_device_only_title),
                message = stringResource(R.string.area_save_device_only_message),
                onRetry = onRetry,
                secondaryActionLabel = stringResource(R.string.area_save_continue),
                onSecondaryAction = onContinue,
                modifier = modifier,
            )
        is SaveSettingsResult.Rejected ->
            RecoveryState(
                title = stringResource(R.string.area_save_rejected_title),
                message = stringResource(R.string.area_save_rejected_message),
                onRetry = onRetry,
                modifier = modifier,
            )
        is SaveSettingsResult.Failed ->
            RecoveryState(
                title = stringResource(problem.failure.titleRes()),
                message = stringResource(problem.failure.messageRes()),
                onRetry = onRetry,
                modifier = modifier,
            )
    }
}

private fun Supplier.labelRes(): Int = when (this) {
    Supplier.ESKOM -> R.string.area_supplier_eskom
    Supplier.MUNICIPAL -> R.string.area_supplier_municipal
    Supplier.UNKNOWN -> R.string.area_supplier_unknown
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
