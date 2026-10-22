package com.example.loadwatch.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.loadwatch.R

// Asks for an optional short label, such as Home or Work, with one-tap suggestions. A label longer than
// maxLength is rejected inline and cannot be confirmed.
@Composable
fun LabelDialog(
    title: String,
    initialLabel: String,
    suggestions: List<String>,
    maxLength: Int,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    val tooLong = label.trim().length > maxLength

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_dialog_field)) },
                    singleLine = true,
                    isError = tooLong || errorMessage != null,
                    supportingText = {
                        Text(
                            when {
                                tooLong -> stringResource(R.string.label_dialog_too_long, maxLength)
                                errorMessage != null -> errorMessage
                                else -> stringResource(R.string.label_dialog_count, label.trim().length, maxLength)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(onClick = { label = suggestion }, label = { Text(suggestion) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label.trim()) }, enabled = !tooLong && !isSaving) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
