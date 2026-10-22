package com.example.loadwatch.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.loadwatch.R
import com.example.loadwatch.core.common.LogScreenLifecycle

data class PlaceholderAction(
    val label: String,
    val onClick: () -> Unit,
    val isPrimary: Boolean = false,
)

// Shared layout for the Part 2 screens until each one is built out: the screen id from the catalogue,
// its title, its purpose, and the actions that drive navigation.
@Composable
fun PlaceholderScreen(
    screenId: String,
    title: String,
    purpose: String,
    modifier: Modifier = Modifier,
    actions: List<PlaceholderAction> = emptyList(),
) {
    LogScreenLifecycle(screenId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.placeholder_screen_label, screenId),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = purpose,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
        }
        actions.forEach { action ->
            if (action.isPrimary) {
                Button(onClick = action.onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(action.label)
                }
            } else {
                OutlinedButton(onClick = action.onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(action.label)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    LoadWatchTheme {
        Surface {
            PlaceholderScreen(
                screenId = "S04",
                title = "Home",
                purpose = "Preview",
                actions = listOf(PlaceholderAction("Primary", {}, isPrimary = true), PlaceholderAction("Secondary", {})),
            )
        }
    }
}
