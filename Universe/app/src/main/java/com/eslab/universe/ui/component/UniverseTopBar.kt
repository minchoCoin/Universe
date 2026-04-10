package com.eslab.universe.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseTopBar(
    selectedModelName: String?,
    onOpenHistory: () -> Unit,
    onShowModelPicker: () -> Unit,
    onShowGenerationSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(text = "Universe")
                Text(
                    text = selectedModelName ?: "No model selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open history",
                )
            }
        },
        actions = {
            IconButton(onClick = onShowGenerationSettings) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Open generation settings",
                )
            }
            OutlinedButton(onClick = onShowModelPicker) {
                Text(text = "Model")
            }
        },
    )
}
