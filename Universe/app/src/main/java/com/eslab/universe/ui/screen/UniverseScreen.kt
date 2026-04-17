package com.eslab.universe.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eslab.universe.data.DownloadableModel
import com.eslab.universe.ui.UniverseUiState
import com.eslab.universe.ui.component.ChatComposer
import com.eslab.universe.ui.component.ChatMessageList
import com.eslab.universe.ui.component.GenerationSettingsDialog
import com.eslab.universe.ui.component.HistoryDrawerContent
import com.eslab.universe.ui.component.ModelPickerDialog
import com.eslab.universe.ui.component.StatusStrip
import com.eslab.universe.ui.component.UniverseTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseScreen(
    uiState: UniverseUiState,
    onDraftChange: (String) -> Unit,
    onAppendTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onSelectModel: (DownloadableModel) -> Unit,
    onDownloadModel: (DownloadableModel) -> Unit,
    onShowModelPicker: () -> Unit,
    onShowGenerationSettings: () -> Unit,
    onHideGenerationSettings: () -> Unit,
    onUpdateConversationSettings: (Int, Double, Double, String) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeSession = uiState.sessions.first { it.id == uiState.activeSessionId }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawerContent(
                sessions = uiState.sessions,
                activeSessionId = uiState.activeSessionId,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectChat = { sessionId ->
                    onSelectChat(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = { sessionId ->
                    onDeleteChat(sessionId)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                UniverseTopBar(
                    selectedModelName = uiState.selectedModel?.displayName,
                    onOpenHistory = { scope.launch { drawerState.open() } },
                    onShowModelPicker = onShowModelPicker,
                    onShowGenerationSettings = onShowGenerationSettings,
                )
            },
            bottomBar = {
                ChatComposer(
                    value = uiState.draftMessage,
                    enabled = !uiState.isGenerating && uiState.selectedModel != null && !uiState.isEngineLoading,
                    isGenerating = uiState.isGenerating,
                    onValueChange = onDraftChange,
                    onTranscript = onAppendTranscript,
                    onSend = onSend,
                    onStop = onStop,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF071019),
                                Color(0xFF0B1724),
                                Color(0xFF102030),
                            ),
                        ),
                    )
                    .padding(innerPadding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    StatusStrip(
                        statusMessage = uiState.statusMessage,
                        backend = uiState.activeBackend,
                    )
                    ChatMessageList(
                        messages = activeSession.messages,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp),
                    )
                }

                if (uiState.isModelPickerVisible) {
                    ModelPickerDialog(
                        models = uiState.models,
                        downloadedModelIds = uiState.downloadedModelIds,
                        selectedModel = uiState.selectedModel,
                        isDownloading = uiState.downloadState.isDownloading,
                        progress = uiState.downloadState.progress,
                        receivedBytes = uiState.downloadState.receivedBytes,
                        totalBytes = uiState.downloadState.totalBytes,
                        downloadError = uiState.downloadState.errorMessage,
                        isEngineLoading = uiState.isEngineLoading,
                        onDownloadModel = onDownloadModel,
                        onSelectModel = onSelectModel,
                    )
                }

                if (uiState.isGenerationSettingsVisible) {
                    GenerationSettingsDialog(
                        settings = uiState.conversationSettings,
                        onDismiss = onHideGenerationSettings,
                        onSave = onUpdateConversationSettings,
                    )
                }
            }
        }
    }
}
