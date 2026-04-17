package com.eslab.universe.ui.component

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun ChatComposer(
    value: String,
    enabled: Boolean,
    isGenerating: Boolean,
    onValueChange: (String) -> Unit,
    onTranscript: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableFloatStateOf(0f) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        audioLevel = 0f
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return
        }

        val recognizer = speechRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            speechRecognizer = it
        }

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    audioLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListening = false
                    audioLevel = 0f
                }

                override fun onError(error: Int) {
                    isListening = false
                    audioLevel = 0f
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    audioLevel = 0f
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (transcript.isNotBlank()) {
                        onTranscript(transcript)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        isListening = true
        audioLevel = 0.15f
        recognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

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
                .padding(end = 72.dp),
            enabled = enabled,
            minLines = 1,
            maxLines = 5,
            placeholder = { Text(text = "Type a message") },
            trailingIcon = {
                VoiceInputAffordance(
                    isListening = isListening,
                    audioLevel = audioLevel,
                    enabled = enabled && !isGenerating,
                    onClick = {
                        when {
                            isListening -> stopListening()
                            hasAudioPermission(context) -> startListening()
                            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            },
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

@Composable
private fun VoiceInputAffordance(
    isListening: Boolean,
    audioLevel: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (isListening) Color(0x33FF8A65) else Color.Transparent,
        border = if (isListening) StrokeBorder else null,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = if (isListening) 78.dp else 40.dp),
        ) {
            if (isListening) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaveformIndicator(audioLevel = audioLevel)
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop voice input",
                        tint = Color(0xFFFF8A65),
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = LocalContentColor.current.copy(alpha = 0.72f),
                )
            }
        }
    }
}

private val StrokeBorder = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF8A65))

@Composable
private fun WaveformIndicator(audioLevel: Float) {
    Row(
        modifier = Modifier
            .width(24.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { index ->
            val barLevel = when (index) {
                0, 3 -> audioLevel * 0.55f + 0.2f
                1, 2 -> audioLevel * 0.9f + 0.25f
                else -> audioLevel
            }.coerceIn(0.15f, 1f)

            Canvas(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp),
            ) {
                val width = size.width
                val minBarHeight = size.height * 0.2f
                val barHeight = max(minBarHeight, min(size.height, size.height * barLevel))
                val top = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = Color(0xFFFF8A65),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                    size = androidx.compose.ui.geometry.Size(width, barHeight),
                    cornerRadius = CornerRadius(width, width),
                )
            }
        }
    }
}

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
