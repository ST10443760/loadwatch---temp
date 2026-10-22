package com.example.loadwatch.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController

// App shell: hosts the navigation graph inside a Scaffold.
@Composable
fun LoadWatchApp() {
    val navController = rememberNavController()

    Scaffold { innerPadding ->
        LoadWatchNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
