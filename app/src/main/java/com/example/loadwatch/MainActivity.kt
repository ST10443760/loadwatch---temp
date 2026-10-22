package com.example.loadwatch

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.loadwatch.core.common.LogTags
import com.example.loadwatch.core.designsystem.LoadWatchTheme
import com.example.loadwatch.navigation.LoadWatchApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LogTags.LIFECYCLE, "MainActivity onCreate (restored: ${savedInstanceState != null})")
        // Dark first, so the system bars use light icons over the slate surface
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LoadWatchTheme {
                LoadWatchApp()
            }
        }
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
