package com.eslab.universe.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eslab.universe.data.ConversationSettings

@Composable
fun GenerationSettingsDialog(
    settings: ConversationSettings,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Double, String) -> Unit,
) {
    var topKText by remember(settings) { mutableStateOf(settings.topK.toString()) }
    var topPText by remember(settings) { mutableStateOf(settings.topP.toString()) }
    var temperatureText by remember(settings) { mutableStateOf(settings.temperature.toString()) }
    var systemInstruction by remember(settings) { mutableStateOf(settings.systemInstruction) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Generation settings") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = topKText,
                    onValueChange = { topKText = it },
                    label = { Text("TopK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = topPText,
                    onValueChange = { topPText = it },
                    label = { Text("TopP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = { temperatureText = it },
                    label = { Text("Temperature") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = systemInstruction,
                    onValueChange = { systemInstruction = it },
                    label = { Text("System instruction") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        topKText.toIntOrNull()?.coerceAtLeast(1) ?: settings.topK,
                        topPText.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: settings.topP,
                        temperatureText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: settings.temperature,
                        systemInstruction.ifBlank { settings.systemInstruction },
                    )
                },
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.padding(8.dp),
    )
}
