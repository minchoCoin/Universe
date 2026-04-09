package com.eslab.universe.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatComposer(
    value: String,
    enabled: Boolean,
    isGenerating: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .consumeWindowInsets(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 68.dp),
            enabled = enabled,
            minLines = 1,
            maxLines = 5,
            placeholder = { Text(text = "Type a message") },
        )
        FilledIconButton(
            onClick = if (isGenerating) onStop else onSend,
            enabled = if (isGenerating) true else enabled && value.isNotBlank(),
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.BottomEnd),
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isGenerating) "Stop generation" else "Send",
            )
        }
    }
}
