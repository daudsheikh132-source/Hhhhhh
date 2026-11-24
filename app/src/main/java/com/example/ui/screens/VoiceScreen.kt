package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.AppStrings
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText
import com.example.viewmodel.VoiceViewModel
import java.util.Locale

@Composable
fun VoiceScreen(
    strings: AppStrings,
    language: AppLanguage,
    personality: AiPersonality,
    memoryContextPrompt: String,
    speechRate: Float,
    autoSpeak: Boolean,
    voiceViewModel: VoiceViewModel,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isListening by voiceViewModel.isListening.collectAsStateWithLifecycle()
    val isThinking by voiceViewModel.isThinking.collectAsStateWithLifecycle()
    val isSpeaking by voiceViewModel.isSpeaking.collectAsStateWithLifecycle()
    val isMuted by voiceViewModel.isMuted.collectAsStateWithLifecycle()
    val rmsLevel by voiceViewModel.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by voiceViewModel.partialTranscript.collectAsStateWithLifecycle()
    val turns by voiceViewModel.turns.collectAsStateWithLifecycle()
    val errorMessage by voiceViewModel.errorMessage.collectAsStateWithLifecycle()

    var showMicRationale by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val recognitionAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }

    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        voiceViewModel.setSpeaking(true)
                    }
                    override fun onDone(utteranceId: String?) {
                        voiceViewModel.setSpeaking(false)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        voiceViewModel.setSpeaking(false)
                    }
                })
            }
        }
        engine
    }

    val speechRecognizer = remember(context, recognitionAvailable) {
        if (recognitionAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun speakReply(text: String) {
        if (!isMuted && autoSpeak && ttsReady) {
            tts.language = Locale.forLanguageTag(language.localeTag)
            tts.setSpeechRate(speechRate)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_${System.currentTimeMillis()}")
        }
    }

    DisposableEffect(speechRecognizer, language, personality, memoryContextPrompt) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                voiceViewModel.setListening(true)
                voiceViewModel.setError(null)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                voiceViewModel.updateRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                voiceViewModel.setListening(false)
            }

            override fun onError(error: Int) {
                voiceViewModel.setListening(false)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please tap talk and speak clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out. Tap talk to try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition requires an active internet connection."
                    else -> "Speech recognition error (code $error)."
                }
                voiceViewModel.setError(msg)
            }

            override fun onResults(results: Bundle?) {
                voiceViewModel.setListening(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    voiceViewModel.processSpokenQuery(
                        spokenText = spoken,
                        personality = personality,
                        language = language,
                        memoryContextPrompt = memoryContextPrompt,
                        onReplyReady = { reply -> speakReply(reply) }
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    voiceViewModel.updatePartialTranscript(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {}
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            tts.stop()
            tts.shutdown()
        }
    }

    fun startListeningInternal() {
        if (!recognitionAvailable || speechRecognizer == null) {
            voiceViewModel.setError(
                "Android Speech Recognition service is not installed or available on this device/emulator."
            )
            return
        }
        tts.stop()
        voiceViewModel.setSpeaking(false)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        voiceViewModel.setListening(true)
        speechRecognizer.startListening(intent)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListeningInternal()
        } else {
            voiceViewModel.setError("Microphone permission denied. Enable it in Android Settings to use Voice Chat.")
        }
    }

    fun onToggleTalk() {
        if (isListening) {
            speechRecognizer?.stopListening()
            voiceViewModel.setListening(false)
        } else if (isSpeaking) {
            tts.stop()
            voiceViewModel.setSpeaking(false)
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startListeningInternal()
            } else {
                showMicRationale = true
            }
        }
    }

    val orbScale by animateFloatAsState(
        targetValue = if (isListening) 1f + rmsLevel * 0.25f else if (isSpeaking) 1.08f else 1f,
        animationSpec = tween(180),
        label = "voice_orb_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("voice_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onToggleLanguage,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("voice_language_button")
            ) {
                Text(if (language == AppLanguage.BENGALI) "বাংলা (bn-BD)" else "English (en-US)")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        voiceViewModel.toggleMute()
                        if (!isMuted) {
                            tts.stop()
                            voiceViewModel.setSpeaking(false)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isMuted) strings.unmuteVoice else strings.muteVoice,
                        tint = if (isMuted) ErrorCoral else BrandCyan
                    )
                }

                IconButton(
                    onClick = { voiceViewModel.clearHistory() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear voice transcript",
                        tint = SlateText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animated Voice Orb
        Box(
            modifier = Modifier
                .size(168.dp)
                .scale(orbScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandCyan.copy(alpha = 0.35f),
                            BrandPurple.copy(alpha = 0.28f),
                            NavySurfaceElevated
                        )
                    )
                )
                .border(
                    width = 2.5.dp,
                    brush = Brush.linearGradient(listOf(BrandPurple, BrandCyan)),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isThinking -> CircularProgressIndicator(color = BrandCyan, strokeWidth = 3.dp)
                isListening -> Icon(Icons.Rounded.Mic, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(54.dp))
                isSpeaking -> Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = BrandPurple, modifier = Modifier.size(54.dp))
                else -> Icon(Icons.Rounded.Mic, contentDescription = null, tint = PureWhite, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when {
                isListening -> strings.listening
                isThinking -> "Thinking..."
                isSpeaking -> strings.speaking
                else -> strings.tapMicToSpeak
            },
            style = MaterialTheme.typography.titleMedium,
            color = BrandCyan,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        if (partialTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\"$partialTranscript\"",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateText,
                textAlign = TextAlign.Center
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = ErrorCoral.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage!!,
                    color = ErrorCoral,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transcript Turns
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(turns) { turn ->
                Surface(
                    color = NavySurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NavyOutline, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "You: ${turn.userText}",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandCyan
                        )
                        Text(
                            text = turn.aiText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Start / Stop Voice Button
        Button(
            onClick = { onToggleTalk() },
            enabled = !isThinking,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening || isSpeaking) ErrorCoral else BrandPurple,
                contentColor = PureWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("voice_talk_button")
        ) {
            Icon(
                imageVector = if (isListening || isSpeaking) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isListening || isSpeaking) strings.stopVoice else strings.startVoice,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            title = { Text(strings.voicePermissionTitle) },
            text = { Text(strings.voicePermissionBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMicRationale = false
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text(strings.grantPermission)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
