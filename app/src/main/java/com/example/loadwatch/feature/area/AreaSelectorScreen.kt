package com.example.loadwatch.feature.area

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderAction
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S05 Area Selector: switches the viewed area or adds a favourite from search, recents or favourites.
// Opened from Home, and returns there once an area is chosen.
@Composable
fun AreaSelectorScreen(
    onAreaSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        screenId = stringResource(R.string.area_selector_screen_id),
        title = stringResource(R.string.area_selector_title),
        purpose = stringResource(R.string.area_selector_purpose),
        modifier = modifier,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.area_selector_use_area), onAreaSelected, isPrimary = true),
        ),
    )
}
