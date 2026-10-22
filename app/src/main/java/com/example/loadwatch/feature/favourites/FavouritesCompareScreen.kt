package com.example.loadwatch.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S08 Favourites and Compare: each saved area with its next event and countdown on one screen
// (FR-FAV-01 to 03).
@Composable
fun FavouritesCompareScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        screenId = stringResource(R.string.favourites_screen_id),
        title = stringResource(R.string.favourites_title),
        purpose = stringResource(R.string.favourites_purpose),
        modifier = modifier,
    )
}
