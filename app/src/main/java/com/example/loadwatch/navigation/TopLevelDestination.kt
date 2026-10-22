package com.example.loadwatch.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import com.example.loadwatch.R

// The five bottom navigation destinations, in the order the mockups show them
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    HOME(HomeRoute, Icons.Filled.Home, R.string.nav_home),
    SCHEDULE(ScheduleRoute, Icons.Filled.DateRange, R.string.nav_schedule),
    REPORT(ReportRoute, Icons.Filled.Warning, R.string.nav_report),
    COMPARE(CompareRoute, Icons.Filled.Favorite, R.string.nav_compare),
    SETTINGS(SettingsRoute, Icons.Filled.Settings, R.string.nav_settings),
}

// Which tab a back stack entry belongs to, or null outside the hub (S01 to S03 have no bottom bar).
// S05 Area Selector keeps the tab it was opened from selected: Compare when adding a favourite, otherwise Home.
fun NavBackStackEntry.topLevelDestination(): TopLevelDestination? {
    if (destination.hasRoute(AreaSelectorRoute::class)) {
        return if (toRoute<AreaSelectorRoute>().addFavourite) TopLevelDestination.COMPARE else TopLevelDestination.HOME
    }
    return TopLevelDestination.entries.firstOrNull { tab ->
        destination.hierarchy.any { it.hasRoute(tab.route::class) }
    }
}
