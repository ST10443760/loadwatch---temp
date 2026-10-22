package com.example.loadwatch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.loadwatch.feature.area.AreaOnboardingScreen
import com.example.loadwatch.feature.area.AreaSelectorScreen
import com.example.loadwatch.feature.auth.LoginScreen
import com.example.loadwatch.feature.auth.SplashScreen
import com.example.loadwatch.feature.favourites.FavouritesCompareScreen
import com.example.loadwatch.feature.home.HomeScreen
import com.example.loadwatch.feature.report.CommunityConfirmationScreen
import com.example.loadwatch.feature.schedule.ScheduleScreen
import com.example.loadwatch.feature.settings.SettingsScreen

// The navigation wireflow (design doc Figure 3): S01 Splash -> S02 SSO -> S03 Onboarding -> S04 Home, with
// Home as the hub for S05 Area, S06 Schedule, S07 Report, S08 Compare and S09 Settings. No destination is more
// than two taps from Home. This is the only place that knows about every feature; features never import
// each other and only expose navigation callbacks.
@Composable
fun LoadWatchNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        modifier = modifier,
    ) {
        // Session wrapper: each step replaces the back stack so Back never returns to it
        composable<SplashRoute> {
            SplashScreen(
                onSignedOut = { navController.navigateClearingBackStack(LoginRoute()) },
                onNeedsArea = { navController.navigateClearingBackStack(AreaOnboardingRoute) },
                onReady = { navController.navigateClearingBackStack(HomeRoute) },
            )
        }
        composable<LoginRoute> { entry ->
            LoginScreen(
                sessionExpired = entry.toRoute<LoginRoute>().sessionExpired,
                onNeedsArea = { navController.navigateClearingBackStack(AreaOnboardingRoute) },
                onReady = { navController.navigateClearingBackStack(HomeRoute) },
            )
        }
        composable<AreaOnboardingRoute> {
            AreaOnboardingScreen(onAreaConfirmed = { navController.navigateClearingBackStack(HomeRoute) })
        }

        // Hub
        composable<HomeRoute> {
            HomeScreen(
                onChangeArea = { navController.navigate(AreaSelectorRoute) },
                onConfirmOutage = { navController.navigateToTopLevel(ReportRoute) },
            )
        }
        composable<AreaSelectorRoute> {
            AreaSelectorScreen(onAreaSelected = { navController.popBackStack() })
        }
        composable<ScheduleRoute> { ScheduleScreen() }
        composable<ReportRoute> {
            CommunityConfirmationScreen(onChooseArea = { navController.navigate(AreaSelectorRoute) })
        }
        composable<CompareRoute> { FavouritesCompareScreen() }
        composable<SettingsRoute> {
            // Sign-out returns to S02 with nothing behind it (FR-AUTH-04)
            SettingsScreen(onSignedOut = { navController.navigateClearingBackStack(LoginRoute()) })
        }
    }
}

// Replaces the whole back stack with route, so Back from it leaves the app
fun NavController.navigateClearingBackStack(route: Any) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

// Switches between the hub's main destinations, keeping Home at the bottom of the stack and restoring each
// destination's saved state when it is reselected
fun NavController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
