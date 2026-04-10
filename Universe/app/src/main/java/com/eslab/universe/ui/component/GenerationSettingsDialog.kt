package com.eslab.universe.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eslab.universe.data.ConversationSettings
import kotlin.math.roundToInt
import java.util.Locale

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
                    onValueChange = { input ->
                        if (input.all(Char::isDigit)) {
                            topKText = input
                        }
                    },
                    label = { Text("TopK") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Slider(
                    value = topKText.toFloatOrNull()?.coerceIn(0f, 64f) ?: settings.topK.toFloat(),
                    onValueChange = { value -> topKText = value.roundToInt().toString() },
                    valueRange = 0f..64f,
                )
                Text(text = "Range: 0-64")
                OutlinedTextField(
                    value = topPText,
                    onValueChange = { input ->
                        if (input.count { it == '.' } <= 1 && input.all { it.isDigit() || it == '.' }) {
                            topPText = input
                        }
                    },
                    label = { Text("TopP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Slider(
                    value = topPText.toFloatOrNull()?.coerceIn(0f, 1f) ?: settings.topP.toFloat(),
                    onValueChange = { value -> topPText = String.format(Locale.US, "%.2f", value) },
                    valueRange = 0f..1f,
                )
                Text(text = "Range: 0-1")
                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = { input ->
                        if (input.count { it == '.' } <= 1 && input.all { it.isDigit() || it == '.' }) {
                            temperatureText = input
                        }
                    },
                    label = { Text("Temperature") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Slider(
                    value = temperatureText.toFloatOrNull()?.coerceIn(0f, 2f) ?: settings.temperature.toFloat(),
                    onValueChange = { value -> temperatureText = String.format(Locale.US, "%.2f", value) },
                    valueRange = 0f..2f,
                )
                Text(text = "Range: 0-2")
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
                        topKText.toIntOrNull()?.coerceIn(0, 64) ?: settings.topK,
                        topPText.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: settings.topP,
                        temperatureText.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: settings.temperature,
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
