package com.eslab.universe.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eslab.universe.data.DownloadableModel
import java.util.Locale

@Composable
fun ModelPickerDialog(
    models: List<DownloadableModel>,
    downloadedModelIds: Set<String>,
    selectedModel: DownloadableModel?,
    isDownloading: Boolean,
    progress: Float?,
    receivedBytes: Long,
    totalBytes: Long,
    downloadError: String?,
    isEngineLoading: Boolean,
    onDownloadModel: (DownloadableModel) -> Unit,
    onSelectModel: (DownloadableModel) -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {},
        title = { Text(text = "Choose a model") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "The app downloads the model on demand instead of bundling it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                models.forEach { model ->
                    val isDownloaded = downloadedModelIds.contains(model.id)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 2.dp,
                        color = if (selectedModel?.id == model.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "${model.repoId} - ${model.sizeLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = model.summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = if (isDownloaded) "Downloaded" else "Download required",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDownloaded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (isDownloading) {
                                LinearProgressIndicator(
                                    progress = { progress ?: 0f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = formatDownloadProgress(
                                        receivedBytes = receivedBytes,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (downloadError != null) {
                                Text(
                                    text = downloadError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { onDownloadModel(model) },
                                    enabled = !isDownloaded && !isDownloading && !isEngineLoading,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                    )
                                    Text(text = "Download")
                                }
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(model.modelPageUrl)),
                                        )
                                    },
                                ) {
                                    Text(text = "Page")
                                }
                            }
                            Button(
                                onClick = { onSelectModel(model) },
                                enabled = isDownloaded && !isDownloading && !isEngineLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isEngineLoading && selectedModel?.id == model.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    Text(text = "Start with this model")
                                }
                            }
                        }
                    }
                }
                Divider()
                Text(
                    text = "The model is fetched directly from Hugging Face when selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

private fun formatDownloadProgress(
    receivedBytes: Long,
    totalBytes: Long,
    progress: Float?,
): String {
    val receivedMb = receivedBytes / 1024f / 1024f
    if (totalBytes <= 0L) {
        return String.format(Locale.US, "%.1f MB downloaded", receivedMb)
    }

    val totalMb = totalBytes / 1024f / 1024f
    val percent = ((progress ?: (receivedBytes.toFloat() / totalBytes.toFloat())) * 100f)
        .coerceIn(0f, 100f)
    return String.format(
        Locale.US,
        "%.1f MB / %.1f MB (%.1f%%)",
        receivedMb,
        totalMb,
        percent,
    )
}
