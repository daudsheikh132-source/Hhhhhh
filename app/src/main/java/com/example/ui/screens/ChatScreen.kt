package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.localization.AppStrings
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.model.MemoryCategory
import com.example.model.PendingMemorySuggestion
import com.example.model.UiState
import com.example.ui.components.ChatHistory
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.BrandPurpleDark
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText
import com.example.util.ImageUtils
import kotlinx.coroutines.launch
import java.util.Locale

object ChatInputUtils {
    fun appendSpokenText(existingText: String, spokenText: String): String {
        val cleanSpoken = spokenText.trim()
        if (cleanSpoken.isEmpty()) return existingText
        if (existingText.isBlank()) return cleanSpoken
        return if (existingText.last().isWhitespace()) {
            existingText + cleanSpoken
        } else {
            "$existingText $cleanSpoken"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    strings: AppStrings,
    language: AppLanguage,
    personality: AiPersonality,
    activeConversationId: String?,
    conversationsState: UiState<List<Conversation>>,
    messages: List<ChatMessage>,
    streamingReply: String?,
    isAiResponding: Boolean,
    chatError: String?,
    pendingMemorySuggestion: PendingMemorySuggestion?,
    speechRate: Float,
    onSelectPersonality: (AiPersonality) -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onSendMessage: (String, List<Bitmap>) -> Unit,
    onStopGenerating: () -> Unit,
    onDismissError: () -> Unit,
    onRequestSaveMemory: (String) -> Unit,
    onApproveMemory: (String, String, MemoryCategory, Boolean) -> Unit,
    onDismissMemorySuggestion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    val attachedBitmaps = remember { mutableStateListOf<Bitmap>() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraRationale by remember { mutableStateOf(false) }
    var showMicRationale by remember { mutableStateOf(false) }
    var showPersonalityMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var isVoiceInputListening by remember { mutableStateOf(false) }
    var voicePartialTranscript by remember { mutableStateOf("") }
    var voiceInputError by remember { mutableStateOf<String?>(null) }
    val latestInputText by rememberUpdatedState(inputText)
    val speechRecognitionAvailable = remember(context) { SpeechRecognizer.isRecognitionAvailable(context) }

    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
        engine
    }

    val voiceInputRecognizer = remember(context, speechRecognitionAvailable) {
        if (speechRecognitionAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(voiceInputRecognizer) {
        voiceInputRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isVoiceInputListening = true
                voiceInputError = null
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isVoiceInputListening = false
            }

            override fun onError(error: Int) {
                isVoiceInputListening = false
                voicePartialTranscript = ""
                voiceInputError = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please tap the mic and speak clearly."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognition requires an active internet connection."
                    else -> "Speech recognition error (code $error)."
                }
            }

            override fun onResults(results: Bundle?) {
                isVoiceInputListening = false
                voicePartialTranscript = ""
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    inputText = ChatInputUtils.appendSpokenText(latestInputText, spoken)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    voicePartialTranscript = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {}
    }

    DisposableEffect(tts, voiceInputRecognizer) {
        onDispose {
            tts.stop()
            tts.shutdown()
            voiceInputRecognizer?.destroy()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    if (attachedBitmaps.size < 5) {
                        ImageUtils.decodeScaledBitmap(context, uri)?.let { bmp ->
                            attachedBitmaps.add(bmp)
                        }
                    }
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                if (attachedBitmaps.size < 5) {
                    ImageUtils.decodeScaledBitmap(context, uri)?.let { bmp ->
                        attachedBitmaps.add(bmp)
                    }
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ImageUtils.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun startVoiceInputListening() {
        if (!speechRecognitionAvailable || voiceInputRecognizer == null) {
            voiceInputError = "Android Speech Recognition service is not available on this device."
            return
        }
        voiceInputError = null
        voicePartialTranscript = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        isVoiceInputListening = true
        try {
            voiceInputRecognizer.startListening(intent)
        } catch (e: Exception) {
            isVoiceInputListening = false
            voiceInputError = e.localizedMessage ?: "Unable to start speech recognition."
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceInputListening()
        } else {
            voiceInputError = "Microphone permission denied. Enable it in Android Settings to use Voice Input."
        }
    }

    fun toggleVoiceInputFlow() {
        if (isVoiceInputListening) {
            try {
                voiceInputRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            isVoiceInputListening = false
            voicePartialTranscript = ""
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoiceInputListening()
        } else {
            showMicRationale = true
        }
    }

    fun launchCameraFlow() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = ImageUtils.createCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            showCameraRationale = true
        }
    }

    LaunchedEffect(messages.size, streamingReply, isAiResponding) {
        val total = messages.size + (if (isAiResponding || streamingReply != null) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_screen")
    ) {
        // Top Header with Personality Switcher & New Chat
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    val pLabel = if (language == AppLanguage.BENGALI) personality.titleBn else personality.titleEn
                    Surface(
                        color = BrandPurple.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, BrandCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { showPersonalityMenu = true }
                            .testTag("chat_personality_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = personalityIcon(personality),
                                contentDescription = pLabel,
                                tint = BrandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = pLabel,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showPersonalityMenu,
                        onDismissRequest = { showPersonalityMenu = false }
                    ) {
                        AiPersonality.entries.forEach { mode ->
                            val title = if (language == AppLanguage.BENGALI) mode.titleBn else mode.titleEn
                            val sub = if (language == AppLanguage.BENGALI) mode.subtitleBn else mode.subtitleEn
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(title, fontWeight = FontWeight.SemiBold)
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = SlateText)
                                    }
                                },
                                leadingIcon = {
                                    Icon(personalityIcon(mode), contentDescription = null, tint = BrandCyan)
                                },
                                onClick = {
                                    onSelectPersonality(mode)
                                    showPersonalityMenu = false
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = strings.chatHistory,
                            tint = BrandCyan
                        )
                    }

                    OutlinedButton(
                        onClick = onNewChat,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("chat_new_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.newChat, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Memory Consent Banner
        AnimatedVisibility(visible = pendingMemorySuggestion != null) {
            pendingMemorySuggestion?.let { suggestion ->
                MemoryConsentBanner(
                    strings = strings,
                    suggestion = suggestion,
                    onApprove = {
                        onApproveMemory(
                            suggestion.title,
                            suggestion.content,
                            suggestion.category,
                            suggestion.sensitive
                        )
                    },
                    onDismiss = onDismissMemorySuggestion
                )
            }
        }

        // Messages List or Empty Starter
        if (messages.isEmpty() && !isAiResponding && streamingReply == null) {
            ChatEmptyStarter(
                strings = strings,
                language = language,
                onPromptSelected = { starter ->
                    onSendMessage(starter, emptyList())
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                        onSpeak = {
                            if (ttsReady) {
                                tts.language = Locale.forLanguageTag(language.localeTag)
                                tts.setSpeechRate(speechRate)
                                tts.speak(msg.content, TextToSpeech.QUEUE_FLUSH, null, "msg_${msg.id}")
                            }
                        },
                        onSaveMemory = { onRequestSaveMemory(msg.content) }
                    )
                }

                if (isAiResponding || streamingReply != null) {
                    item(key = "streaming_item") {
                        StreamingAiBubble(
                            text = streamingReply.orEmpty(),
                            typingLabel = strings.aiIsTyping
                        )
                    }
                }
            }
        }

        // Subtle 'is typing...' indicator bar above input while AI is processing
        AnimatedVisibility(visible = isAiResponding) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("chat_typing_indicator"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypingDotsIndicator()
                Text(
                    text = strings.aiIsTyping,
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateText
                )
            }
        }

        // Error Banner
        if (chatError != null) {
            Surface(
                color = ErrorCoral.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatError,
                        color = ErrorCoral,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss error", tint = ErrorCoral)
                    }
                }
            }
        }

        // Attached Images Preview Strip
        AnimatedVisibility(visible = attachedBitmaps.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(attachedBitmaps) { index, bmp ->
                    Box(modifier = Modifier.size(76.dp)) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached preview ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, BrandCyan, RoundedCornerShape(14.dp))
                        )
                        Surface(
                            color = NavySurface.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(24.dp)
                                .clickable { attachedBitmaps.removeAt(index) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = strings.clearImages,
                                    tint = PureWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Input Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                AnimatedVisibility(
                    visible = isVoiceInputListening || voicePartialTranscript.isNotBlank() || voiceInputError != null
                ) {
                    Surface(
                        color = if (voiceInputError != null) ErrorCoral.copy(alpha = 0.14f) else BrandCyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("chat_voice_status_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isVoiceInputListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = if (voiceInputError != null) ErrorCoral else BrandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            val currentVoiceError = voiceInputError
                            Text(
                                text = when {
                                    currentVoiceError != null -> currentVoiceError
                                    voicePartialTranscript.isNotBlank() -> voicePartialTranscript
                                    else -> strings.listening
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentVoiceError != null) ErrorCoral else BrandCyan,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentVoiceError != null) {
                                IconButton(
                                    onClick = { voiceInputError = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Dismiss voice error",
                                        tint = ErrorCoral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = strings.attachFromGallery,
                            tint = BrandCyan
                        )
                    }

                    IconButton(
                        onClick = { launchCameraFlow() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = strings.takePhoto,
                            tint = BrandPurple
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                strings.typeMessagePlaceholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandCyan,
                            unfocusedBorderColor = NavyOutline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    IconButton(
                        onClick = { toggleVoiceInputFlow() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isVoiceInputListening) {
                                    ErrorCoral.copy(alpha = 0.22f)
                                } else {
                                    BrandCyan.copy(alpha = 0.14f)
                                }
                            )
                            .testTag("chat_voice_input_button")
                    ) {
                        Icon(
                            imageVector = if (isVoiceInputListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = if (isVoiceInputListening) strings.stopVoiceInput else strings.voiceInput,
                            tint = if (isVoiceInputListening) ErrorCoral else BrandCyan
                        )
                    }

                    if (isAiResponding) {
                        IconButton(
                            onClick = onStopGenerating,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ErrorCoral.copy(alpha = 0.22f))
                                .testTag("chat_stop_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Stop generating",
                                tint = ErrorCoral
                            )
                        }
                    } else {
                        val canSend = inputText.isNotBlank() || attachedBitmaps.isNotEmpty()
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    val imagesCopy = attachedBitmaps.toList()
                                    val promptCopy = inputText
                                    inputText = ""
                                    attachedBitmaps.clear()
                                    onSendMessage(promptCopy, imagesCopy)
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) BrandPurple else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = strings.send,
                                tint = if (canSend) PureWhite else SlateText
                            )
                        }
                    }
                }
            }
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

    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text(strings.cameraPermissionTitle) },
            text = { Text(strings.cameraPermissionBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraRationale = false
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                ) {
                    Text(strings.grantPermission)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showHistorySheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState,
            containerColor = NavySurface
        ) {
            ChatHistory(
                strings = strings,
                language = language,
                activeConversationId = activeConversationId,
                conversationsState = conversationsState,
                onSelectConversation = { convId ->
                    onSelectConversation(convId)
                    showHistorySheet = false
                },
                onRenameConversation = onRenameConversation,
                onDeleteConversation = onDeleteConversation,
                onStartNewChat = {
                    onNewChat()
                    showHistorySheet = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
            )
        }
    }
}

@Composable
private fun ChatEmptyStarter(
    strings: AppStrings,
    language: AppLanguage,
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val starters = if (language == AppLanguage.BENGALI) {
        listOf(
            "আজকের দিনটি আরও ভালো করার ৩টি সহজ পরামর্শ দাও",
            "কোয়ান্টাম কম্পিউটিং সহজ ভাষায় বুঝিয়ে দাও",
            "একটি অনুপ্রেরণামূলক ছোট গল্প লিখে দাও",
            "এই ইংরেজি বাক্যটির বাংলা অনুবাদ করো: Knowledge is power"
        )
    } else {
        listOf(
            "Help me organize my thoughts and priorities for today",
            "Explain Kotlin Coroutines & Flow with a simple example",
            "Write a short imaginative sci-fi story set in Dhaka 2090",
            "Translate 'আপনার দিনটি শুভ হোক' to English and explain"
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = strings.welcomeGreeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.aiIdentityDisclaimer,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateText
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
        items(starters) { prompt ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, NavyOutline, RoundedCornerShape(16.dp))
                    .clickable { onPromptSelected(prompt) }
            ) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onSaveMemory: () -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) BrandPurpleDark else NavySurfaceElevated,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            modifier = Modifier
                .widthIn(max = 330.dp)
                .border(
                    width = 1.dp,
                    color = if (isUser) BrandPurple else NavyOutline,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.hasImage && message.imageCount > 0) {
                    Text(
                        text = "📎 ${message.imageCount} image(s) attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandCyan,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureWhite
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy",
                                tint = SlateText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = BrandCyan,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(onClick = onSaveMemory, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Rounded.BookmarkAdd,
                                contentDescription = "Save to Memory",
                                tint = BrandPurple,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAiBubble(
    text: String,
    typingLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = NavySurfaceElevated,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
            modifier = Modifier
                .widthIn(max = 330.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(BrandPurple, BrandCyan)),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (text.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypingDotsIndicator()
                        Text(
                            text = typingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateText
                        )
                    }
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TypingDotsIndicator()
                }
            }
        }
    }
}

@Composable
private fun TypingDotsIndicator() {
    val transition = rememberInfiniteTransition(label = "typing_dots")
    val a1 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "d1"
    )
    val a2 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 150), RepeatMode.Reverse), label = "d2"
    )
    val a3 by transition.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, delayMillis = 300), RepeatMode.Reverse), label = "d3"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(a1, a2, a3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(BrandCyan)
            )
        }
    }
}

@Composable
private fun MemoryConsentBanner(
    strings: AppStrings,
    suggestion: PendingMemorySuggestion,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = NavySurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, BrandCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.memoryPermissionPrompt, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "\"${suggestion.content}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = SlateText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (suggestion.sensitive) {
                Text(
                    text = strings.sensitiveMemoryWarning,
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorCoral
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text(strings.cancel)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text(strings.save)
                }
            }
        }
    }
}
