package com.example.loadwatch.feature.area

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import com.example.loadwatch.core.designsystem.PlaceholderAction
import com.example.loadwatch.core.designsystem.PlaceholderScreen

// S03 Area Onboarding: establishes the default area on first run using search with disambiguating
// context (FR-AREA-01 to 04).
@Composable
fun AreaOnboardingScreen(
    onAreaConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        screenId = stringResource(R.string.onboarding_screen_id),
        title = stringResource(R.string.onboarding_title),
        purpose = stringResource(R.string.onboarding_purpose),
        modifier = modifier,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.onboarding_confirm_area), onAreaConfirmed, isPrimary = true),
        ),
    )
}
