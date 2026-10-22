package com.example.loadwatch.navigation

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.loadwatch.core.common.LogTags

// App shell: hosts the navigation graph and shows the bottom bar on the hub's destinations.
@Composable
fun LoadWatchApp() {
    val navController = rememberNavController()
    LogNavigationTransitions(navController)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedDestination = backStackEntry?.destination?.topLevelDestination()

    Scaffold(
        bottomBar = {
            if (selectedDestination != null) {
                LoadWatchBottomBar(
                    selected = selectedDestination,
                    onSelect = { navController.navigateToTopLevel(it.route) },
                )
            }
        },
    ) { innerPadding ->
        LoadWatchNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

// Logs every navigation transition as "from -> to" with the back stack depth (NFR-08)
@Composable
private fun LogNavigationTransitions(navController: NavController) {
    DisposableEffect(navController) {
        var previousRoute: String? = null
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            val route = destination.route?.substringAfterLast('.') ?: destination.id.toString()
            val depth = controller.currentBackStack.value.count { it.destination !is NavGraph }
            Log.i(LogTags.NAVIGATION, "${previousRoute ?: "start"} -> $route (back stack depth $depth)")
            previousRoute = route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

@Composable
private fun LoadWatchBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                // The visible label already names the destination for screen readers
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
