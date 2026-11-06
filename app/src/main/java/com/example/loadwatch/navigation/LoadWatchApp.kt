package com.example.loadwatch.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.designsystem.PendingSyncBanner
import com.example.loadwatch.feature.lock.AppLockScreen

// App shell: on launch, asks for biometrics before anything else when biometric unlock is on. The screens are not
// composed at all while locked, so nothing behind the lock is visible or reachable with a screen reader.
@Composable
fun LoadWatchApp(appViewModel: AppViewModel = hiltViewModel()) {
    val lockState by appViewModel.lockState.collectAsStateWithLifecycle()
    when (lockState) {
        // Only as long as reading the settings from Room takes; the window background shows meanwhile
        AppLockState.CHECKING -> Unit
        AppLockState.LOCKED -> AppLockScreen(
            availability = remember { appViewModel.biometricAvailability() },
            onUnlocked = appViewModel::onUnlocked,
            onSignOut = appViewModel::signOutFromLock,
        )
        AppLockState.UNLOCKED -> UnlockedApp(appViewModel)
    }
}

// Hosts the navigation graph, shows the bottom bar on the hub's destinations, returns to S02 when the session
// ends, and offers biometric unlock once after signing in.
@Composable
private fun UnlockedApp(
    appViewModel: AppViewModel,
    syncStatusViewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    LogNavigationTransitions(navController)

    LaunchedEffect(appViewModel) {
        appViewModel.sessionEnded.collect {
            navController.navigateClearingBackStack(LoginRoute(sessionExpired = true))
        }
    }

    LaunchedEffect(appViewModel) {
        appViewModel.signedOutFromLock.collect {
            navController.navigateClearingBackStack(LoginRoute())
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val unsentCount by syncStatusViewModel.unsentCount.collectAsStateWithLifecycle()
    val selectedDestination = backStackEntry?.topLevelDestination()

    // Shown on the hub only, so it never interrupts onboarding
    val showBiometricOffer by appViewModel.showBiometricOffer.collectAsStateWithLifecycle()
    if (showBiometricOffer && selectedDestination != null) {
        BiometricOfferDialog(
            onOpenSettings = {
                appViewModel.dismissBiometricOffer()
                navController.navigateToTopLevel(SettingsRoute)
            },
            onDismiss = appViewModel::dismissBiometricOffer,
        )
    }

    // Android 13+ asks before an app may post notifications. A short explanation comes first, after the biometric
    // offer, and only once; a denial just means stage alerts are not shown, and Settings explains how to allow them.
    val context = LocalContext.current
    val wantsNotificationPermission by appViewModel.wantsNotificationPermission.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        appViewModel.onNotificationPermissionResult(granted)
    }
    if (wantsNotificationPermission && !showBiometricOffer && selectedDestination != null && needsNotificationPermission(context)) {
        NotificationPermissionDialog(
            onContinue = {
                appViewModel.onNotificationPromptShown()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = appViewModel::onNotificationPromptShown,
        )
    }

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
        Column(Modifier.padding(innerPadding)) {
            // Changes made offline wait in the sync queue; the banner shows on the hub until they are sent
            if (selectedDestination != null && unsentCount > 0) {
                PendingSyncBanner(count = unsentCount)
            }
            LoadWatchNavHost(
                navController = navController,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Logs every navigation transition as "from -> to" with where Back now leads (NFR-08). Uses only NavController's
// public API; the back stack itself is restricted to the navigation library.
@Composable
private fun LogNavigationTransitions(navController: NavController) {
    DisposableEffect(navController) {
        var previousRoute: String? = null
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            val route = destination.routeName()
            val back = controller.previousBackStackEntry?.destination?.routeName() ?: "leaves the app"
            Log.i(LogTags.NAVIGATION, "${previousRoute ?: "start"} -> $route (Back: $back)")
            previousRoute = route
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/** One-time dialog after sign-in that offers to turn on biometric unlock in Settings. */
@Composable
private fun BiometricOfferDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.biometric_offer_title)) },
        text = { Text(stringResource(R.string.biometric_offer_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.biometric_offer_open_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.biometric_offer_not_now)) }
        },
    )
}

/** Explains stage alerts before Android 13 and later asks for the notification permission. */
@Composable
private fun NotificationPermissionDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_permission_title)) },
        text = { Text(stringResource(R.string.notification_permission_message)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.notification_permission_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.notification_permission_not_now)) }
        },
    )
}

// Only Android 13 and later ask; earlier versions allow notifications unless the user turns them off
private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Bottom navigation between Home, Schedule, Report, Compare and Settings. */
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

// The route's class name, e.g. "HomeRoute", without its package
private fun NavDestination.routeName(): String = route?.substringBefore('?')?.substringBefore('/')?.substringAfterLast('.') ?: id.toString()
