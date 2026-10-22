package com.example.loadwatch

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.data.model.ThemePreference
import com.example.loadwatch.navigation.AppViewModel
import com.example.loadwatch.navigation.LoadWatchApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LogTags.LIFECYCLE, "MainActivity onCreate (restored: ${savedInstanceState != null})")
        // Dark first, so the system bars start with light icons over the slate surface
        applySystemBars(darkTheme = true)
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themePreference by appViewModel.themePreference.collectAsStateWithLifecycle()
            // The S09 theme applies as soon as it is saved; dark-first until settings exist
            val darkTheme = when (themePreference) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK, null -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(darkTheme) {
                applySystemBars(darkTheme)
                Log.i(LogTags.LIFECYCLE, "Theme applied: ${if (darkTheme) "dark" else "light"} (preference $themePreference)")
            }

            LoadWatchTheme(darkTheme = darkTheme) {
                LoadWatchApp(appViewModel)
            }
        }
    }

    // Light status and navigation bar icons over a dark theme, dark icons over a light one
    private fun applySystemBars(darkTheme: Boolean) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onStart() {
        super.onStart()
        Log.i(LogTags.LIFECYCLE, "MainActivity onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.i(LogTags.LIFECYCLE, "MainActivity onResume")
    }

    override fun onPause() {
        Log.i(LogTags.LIFECYCLE, "MainActivity onPause")
        super.onPause()
    }

    override fun onStop() {
        Log.i(LogTags.LIFECYCLE, "MainActivity onStop")
        super.onStop()
    }

    override fun onDestroy() {
        Log.i(LogTags.LIFECYCLE, "MainActivity onDestroy (finishing: $isFinishing)")
        super.onDestroy()
    }
}
