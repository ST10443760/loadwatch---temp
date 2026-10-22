package com.example.loadwatch.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Amber400,
    onPrimary = Slate950,
    primaryContainer = Amber900,
    onPrimaryContainer = Amber100,
    secondary = Slate300,
    onSecondary = Slate900,
    secondaryContainer = Slate700,
    onSecondaryContainer = Slate100,
    tertiary = Amber400,
    onTertiary = Slate950,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = Slate950,
    surfaceContainerLow = Slate900,
    surfaceContainer = Slate800,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate500,
    outlineVariant = Slate700,
    error = Red400,
    onError = Slate950,
    errorContainer = Red900,
    onErrorContainer = Red100,
)

private val LightColors = lightColorScheme(
    primary = Amber700,
    onPrimary = Color.White,
    primaryContainer = Amber100,
    onPrimaryContainer = Amber900,
    secondary = Slate600,
    onSecondary = Color.White,
    secondaryContainer = Slate200,
    onSecondaryContainer = Slate900,
    tertiary = Amber700,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate200,
    surfaceContainerHighest = Slate300,
    outline = Slate500,
    outlineVariant = Slate300,
    error = Red700,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red900,
)

// Dark first: the app is designed against the dark palette and uses it unless told otherwise.
// The S09 theme preference (system, light or dark) will drive darkTheme once settings are wired in.
// Dynamic colour is deliberately not used, because supply-state colours must stay consistent.
@Composable
fun LoadWatchTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = LoadWatchTypography,
            content = content,
        )
    }
}

object LoadWatchTheme {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}
