package com.eslab.universe.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.eslab.universe.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val KEY_DOWNLOAD_MODEL_NAME = "download_model_name"
const val KEY_DOWNLOAD_URL = "download_url"
const val KEY_DOWNLOAD_VERSION = "download_version"
const val KEY_DOWNLOAD_STORAGE_DIR = "download_storage_dir"
const val KEY_DOWNLOAD_FILE_NAME = "download_file_name"
const val KEY_DOWNLOAD_TOTAL_BYTES = "download_total_bytes"
const val KEY_DOWNLOAD_ACCESS_TOKEN = "download_access_token"
const val KEY_DOWNLOAD_RECEIVED_BYTES = "download_received_bytes"
const val KEY_DOWNLOAD_RATE = "download_rate"
const val KEY_DOWNLOAD_REMAINING_MS = "download_remaining_ms"
const val KEY_DOWNLOAD_ERROR_MESSAGE = "download_error_message"

private const val CHANNEL_ID = "model_download_channel"

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        createNotificationChannel()

        val modelName = inputData.getString(KEY_DOWNLOAD_MODEL_NAME) ?: "Model"
        val urlString = inputData.getString(KEY_DOWNLOAD_URL)
            ?: return@withContext Result.failure(
                Data.Builder().putString(KEY_DOWNLOAD_ERROR_MESSAGE, "Missing download URL.").build(),
            )
        val version = inputData.getString(KEY_DOWNLOAD_VERSION)
            ?: return@withContext Result.failure(
                Data.Builder().putString(KEY_DOWNLOAD_ERROR_MESSAGE, "Missing model version.").build(),
            )
        val storageDir = inputData.getString(KEY_DOWNLOAD_STORAGE_DIR)
            ?: return@withContext Result.failure(
                Data.Builder().putString(KEY_DOWNLOAD_ERROR_MESSAGE, "Missing storage directory.").build(),
            )
        val fileName = inputData.getString(KEY_DOWNLOAD_FILE_NAME)
            ?: return@withContext Result.failure(
                Data.Builder().putString(KEY_DOWNLOAD_ERROR_MESSAGE, "Missing file name.").build(),
            )
        val totalBytes = inputData.getLong(KEY_DOWNLOAD_TOTAL_BYTES, 0L)
        val accessToken = inputData.getString(KEY_DOWNLOAD_ACCESS_TOKEN)

        setForeground(createForegroundInfo(modelName = modelName, progress = 0))

        val outputDir = File(applicationContext.getExternalFilesDir(null), "$storageDir${File.separator}$version")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val tempFile = File(outputDir, "$fileName.part")
        val finalFile = File(outputDir, fileName)

        try {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                if (!accessToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
            }

            val existingBytes = tempFile.takeIf { it.exists() }?.length() ?: 0L
            if (existingBytes > 0L) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
                connection.setRequestProperty("Accept-Encoding", "identity")
            }
            connection.connect()

            if (connection.responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(
                            KEY_DOWNLOAD_ERROR_MESSAGE,
                            "HTTP ${connection.responseCode} while downloading the model.",
                        )
                        .build(),
                )
            }

            val resolvedTotalBytes = resolveTotalBytes(
                connection = connection,
                existingBytes = existingBytes,
                fallbackTotalBytes = totalBytes,
            )

            var downloadedBytes = existingBytes
            var deltaBytes = 0L
            var lastProgressTimestamp = 0L
            val sizeSamples = ArrayDeque<Long>()
            val latencySamples = ArrayDeque<Long>()

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, existingBytes > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        deltaBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTimestamp > 200) {
                            var bytesPerMs = 0f
                            if (lastProgressTimestamp != 0L) {
                                if (sizeSamples.size == 5) sizeSamples.removeFirst()
                                if (latencySamples.size == 5) latencySamples.removeFirst()
                                sizeSamples.addLast(deltaBytes)
                                latencySamples.addLast(now - lastProgressTimestamp)
                                deltaBytes = 0L
                                bytesPerMs = sizeSamples.sum().toFloat() / latencySamples.sum().toFloat()
                            }

                            val remainingMs =
                                if (bytesPerMs > 0f && resolvedTotalBytes > 0L) {
                                    ((resolvedTotalBytes - downloadedBytes) / bytesPerMs).toLong()
                                } else {
                                    -1L
                                }

                            setProgress(
                                Data.Builder()
                                    .putLong(KEY_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                                    .putLong(KEY_DOWNLOAD_TOTAL_BYTES, resolvedTotalBytes)
                                    .putLong(KEY_DOWNLOAD_RATE, (bytesPerMs * 1000).toLong())
                                    .putLong(KEY_DOWNLOAD_REMAINING_MS, remainingMs)
                                    .build(),
                            )

                            val progressPercent =
                                if (resolvedTotalBytes > 0L) {
                                    ((downloadedBytes * 100) / resolvedTotalBytes).toInt()
                                } else {
                                    0
                                }
                            setForeground(
                                createForegroundInfo(
                                    modelName = modelName,
                                    progress = progressPercent.coerceIn(0, 100),
                                ),
                            )
                            lastProgressTimestamp = now
                        }
                    }
                }
            }

            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!tempFile.renameTo(finalFile)) {
                return@withContext Result.failure(
                    Data.Builder()
                        .putString(KEY_DOWNLOAD_ERROR_MESSAGE, "Failed to move the downloaded file.")
                        .build(),
                )
            }

            Result.success()
        } catch (error: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(KEY_DOWNLOAD_ERROR_MESSAGE, error.message ?: "Download failed.")
                    .build(),
            )
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("Model", 0)

    private fun createForegroundInfo(modelName: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading $modelName")
            .setContentText("Download in progress: $progress%")
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun resolveTotalBytes(
        connection: HttpURLConnection,
        existingBytes: Long,
        fallbackTotalBytes: Long,
    ): Long {
        val contentRange = connection.getHeaderField("Content-Range")
        val totalFromContentRange = contentRange
            ?.substringAfterLast("/")
            ?.toLongOrNull()
        if (totalFromContentRange != null && totalFromContentRange > 0L) {
            return totalFromContentRange
        }

        val contentLength = connection.contentLengthLong
        if (contentLength > 0L) {
            return if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                existingBytes + contentLength
            } else {
                contentLength
            }
        }

        return fallbackTotalBytes
    }
}
