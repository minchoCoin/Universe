package com.eslab.universe.data

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.eslab.universe.worker.KEY_DOWNLOAD_ACCESS_TOKEN
import com.eslab.universe.worker.KEY_DOWNLOAD_ERROR_MESSAGE
import com.eslab.universe.worker.KEY_DOWNLOAD_FILE_NAME
import com.eslab.universe.worker.KEY_DOWNLOAD_MODEL_NAME
import com.eslab.universe.worker.KEY_DOWNLOAD_RATE
import com.eslab.universe.worker.KEY_DOWNLOAD_RECEIVED_BYTES
import com.eslab.universe.worker.KEY_DOWNLOAD_REMAINING_MS
import com.eslab.universe.worker.KEY_DOWNLOAD_STORAGE_DIR
import com.eslab.universe.worker.KEY_DOWNLOAD_TOTAL_BYTES
import com.eslab.universe.worker.KEY_DOWNLOAD_URL
import com.eslab.universe.worker.KEY_DOWNLOAD_VERSION
import com.eslab.universe.worker.ModelDownloadWorker
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ChatHistoryMessage(
    val role: ChatRole,
    val text: String,
)

enum class ChatRole {
    USER,
    ASSISTANT,
}

enum class ModelDownloadStatusType {
    NOT_DOWNLOADED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
}

data class ModelDownloadUpdate(
    val status: ModelDownloadStatusType,
    val receivedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val remainingMs: Long = -1L,
    val errorMessage: String? = null,
)

class LiteRtChatRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    private var engine: Engine? = null
    private var currentConversation: Conversation? = null
    private var currentModelId: String? = null
    private var currentBackend: String? = null

    fun modelFile(model: DownloadableModel): File =
        File(
            appContext.getExternalFilesDir(null),
            listOf(model.storageDirName, model.version, model.fileName).joinToString(File.separator),
        )

    fun isDownloaded(model: DownloadableModel): Boolean = modelFile(model).exists()

    fun downloadedModelIds(models: List<DownloadableModel>): Set<String> = models
        .filter(::isDownloaded)
        .mapTo(mutableSetOf()) { it.id }

    fun downloadModel(
        model: DownloadableModel,
        onStatusUpdated: (ModelDownloadUpdate) -> Unit,
    ) {
        val inputData = Data.Builder()
            .putString(KEY_DOWNLOAD_MODEL_NAME, model.displayName)
            .putString(KEY_DOWNLOAD_URL, model.downloadUrl)
            .putString(KEY_DOWNLOAD_VERSION, model.version)
            .putString(KEY_DOWNLOAD_STORAGE_DIR, model.storageDirName)
            .putString(KEY_DOWNLOAD_FILE_NAME, model.fileName)
            .putLong(KEY_DOWNLOAD_TOTAL_BYTES, model.sizeBytes)
            .putString(KEY_DOWNLOAD_ACCESS_TOKEN, model.accessToken)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData)
            .addTag("model:${model.id}")
            .build()

        workManager.enqueueUniqueWork(model.id, ExistingWorkPolicy.REPLACE, request)
        observeDownload(request.id.toString(), model, onStatusUpdated)
    }

    fun cancelDownload(model: DownloadableModel) {
        workManager.cancelUniqueWork(model.id)
    }

    private fun observeDownload(
        workId: String,
        model: DownloadableModel,
        onStatusUpdated: (ModelDownloadUpdate) -> Unit,
    ) {
        val observer = object : Observer<WorkInfo?> {
            override fun onChanged(value: WorkInfo?) {
                if (value == null || value.id.toString() != workId) return

                when (value.state) {
                    WorkInfo.State.ENQUEUED -> {
                        onStatusUpdated(
                            ModelDownloadUpdate(
                                status = ModelDownloadStatusType.IN_PROGRESS,
                                totalBytes = model.sizeBytes,
                            ),
                        )
                    }

                    WorkInfo.State.RUNNING -> {
                        onStatusUpdated(
                            ModelDownloadUpdate(
                                status = ModelDownloadStatusType.IN_PROGRESS,
                                receivedBytes = value.progress.getLong(KEY_DOWNLOAD_RECEIVED_BYTES, 0L),
                                totalBytes = value.progress.getLong(KEY_DOWNLOAD_TOTAL_BYTES, model.sizeBytes),
                                bytesPerSecond = value.progress.getLong(KEY_DOWNLOAD_RATE, 0L),
                                remainingMs = value.progress.getLong(KEY_DOWNLOAD_REMAINING_MS, -1L),
                            ),
                        )
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        onStatusUpdated(
                            ModelDownloadUpdate(
                                status = ModelDownloadStatusType.SUCCEEDED,
                                receivedBytes = model.sizeBytes,
                                totalBytes = model.sizeBytes,
                            ),
                        )
                        workManager.getWorkInfoByIdLiveData(value.id).removeObserver(this)
                    }

                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> {
                        val status =
                            if (value.state == WorkInfo.State.CANCELLED) {
                                ModelDownloadStatusType.NOT_DOWNLOADED
                            } else {
                                ModelDownloadStatusType.FAILED
                            }
                        onStatusUpdated(
                            ModelDownloadUpdate(
                                status = status,
                                errorMessage = value.outputData.getString(KEY_DOWNLOAD_ERROR_MESSAGE)
                                    ?: "Download failed.",
                            ),
                        )
                        workManager.getWorkInfoByIdLiveData(value.id).removeObserver(this)
                    }

                    else -> Unit
                }
            }
        }

        workManager.getWorkInfoByIdLiveData(UUID.fromString(workId)).observeForever(observer)
    }

    suspend fun initializeModel(model: DownloadableModel): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isDownloaded(model)) {
                error("The selected model has not been downloaded yet.")
            }

            if (currentModelId == model.id && engine != null && currentBackend != null) {
                return@runCatching currentBackend!!
            }

            closeConversation()
            closeEngine()

            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

            val modelPath = modelFile(model).absolutePath
            val gpuConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                cacheDir = appContext.cacheDir.absolutePath,
            )

            try {
                val initializedEngine = Engine(gpuConfig)
                initializedEngine.initialize()
                engine = initializedEngine
                currentModelId = model.id
                currentBackend = "GPU"
                "GPU"
            } catch (_: Throwable) {
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = appContext.cacheDir.absolutePath,
                )
                val initializedEngine = Engine(cpuConfig)
                initializedEngine.initialize()
                engine = initializedEngine
                currentModelId = model.id
                currentBackend = "CPU"
                "CPU"
            }
        }
    }

    suspend fun selectConversation(history: List<ChatHistoryMessage>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val activeEngine = engine ?: error("The engine is not initialized.")
            closeConversation()
            val initialMessages = history.map {
                when (it.role) {
                    ChatRole.USER -> Message.user(it.text)
                    ChatRole.ASSISTANT -> Message.model(it.text)
                }
            }
            currentConversation = activeEngine.createConversation(
                ConversationConfig(
                    initialMessages = initialMessages,
                ),
            )
        }
    }

    fun streamReply(prompt: String): Flow<Message> {
        val conversation = currentConversation ?: error("The conversation is not ready.")
        return conversation.sendMessageAsync(prompt).flowOn(Dispatchers.IO)
    }

    suspend fun closeConversation() = withContext(Dispatchers.IO) {
        val conversation = currentConversation ?: return@withContext
        currentConversation = null
        try {
            conversation.close()
        } catch (_: IllegalStateException) {
            // LiteRT-LM throws if the same conversation is closed twice.
        }
    }

    suspend fun closeEngine() = withContext(Dispatchers.IO) {
        engine?.close()
        engine = null
        currentModelId = null
        currentBackend = null
    }

    suspend fun shutdown() {
        closeConversation()
        closeEngine()
    }
}
