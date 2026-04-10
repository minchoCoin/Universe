package com.eslab.universe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eslab.universe.ui.screen.UniverseScreen

@Composable
fun UniverseApp(
    viewModel: UniverseViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    UniverseScreen(
        uiState = uiState,
        onDraftChange = viewModel::updateDraftMessage,
        onSend = viewModel::sendMessage,
        onStop = viewModel::stopGeneration,
        onNewChat = viewModel::createNewChat,
        onSelectChat = viewModel::selectChat,
        onSelectModel = viewModel::selectModel,
        onDownloadModel = viewModel::downloadModel,
        onShowModelPicker = viewModel::showModelPicker,
        onShowGenerationSettings = viewModel::showGenerationSettings,
        onHideGenerationSettings = viewModel::hideGenerationSettings,
        onUpdateConversationSettings = viewModel::updateConversationSettings,
    )
}
