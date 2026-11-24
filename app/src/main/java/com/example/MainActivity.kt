package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AiAssistantService
import com.example.data.MemoryManager
import com.example.data.SoulMateRepository
import com.example.data.attemptAutoSignIn
import com.example.data.signOut
import com.example.firebase.configureAppCheck
import com.example.localization.StringsProvider
import com.example.model.AiPersonality
import com.example.model.AppLanguage
import com.example.model.AppThemeMode
import com.example.model.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageGenScreen
import com.example.ui.screens.IntroScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VoiceScreen
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SoulMateTheme
import com.example.util.ProfileAvatarStore
import com.example.viewmodel.ImageGenViewModel
import com.example.viewmodel.SoulMateViewModel
import com.example.viewmodel.VoiceViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class SoulMateTab(val route: String, val icon: ImageVector) {
    HOME("home", Icons.Rounded.Home),
    CHAT("chat", Icons.AutoMirrored.Rounded.Chat),
    VOICE("voice", Icons.Rounded.Mic),
    STUDIO("studio", Icons.Rounded.Brush),
    MEMORY("memory", Icons.Rounded.Psychology),
    SETTINGS("settings", Icons.Rounded.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            SoulMateRoot()
        }
    }
}

@Composable
private fun SoulMateRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showIntro by rememberSaveable { mutableStateOf(true) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        ProfileAvatarStore.ensureLoaded(context)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = {},
            scope = scope
        )
    }

    SoulMateTheme(themeMode = AppThemeMode.DARK_NAVY) {
        Crossfade(
            targetState = showIntro,
            animationSpec = tween(durationMillis = 450),
            label = "intro_to_home_transition"
        ) { isIntroActive ->
            if (isIntroActive) {
                IntroScreen(onNavigateToHome = { showIntro = false })
            } else if (currentUser == null) {
                AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
            } else {
                val uid = currentUser!!.uid
                key(uid) {
                    AuthenticatedSoulMateShell(
                        currentUserId = uid,
                        onSignOut = {
                            signOut(
                                credentialManager = credentialManager,
                                scope = scope,
                                onSignOutComplete = { currentUser = null }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedSoulMateShell(
    currentUserId: String,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val aiService = remember { AiAssistantService() }

    val mainViewModel: SoulMateViewModel = viewModel(
        key = currentUserId,
        factory = remember(currentUserId, context) {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            SoulMateViewModel.provideFactory(
                repository = SoulMateRepository(db),
                memoryManager = MemoryManager(db),
                aiService = aiService,
                currentUserId = currentUserId
            )
        }
    )
    val voiceViewModel: VoiceViewModel = viewModel(
        key = "voice_$currentUserId",
        factory = remember { VoiceViewModel.provideFactory(aiService) }
    )
    val imageGenViewModel: ImageGenViewModel = viewModel(
        key = "imggen_$currentUserId",
        factory = remember { ImageGenViewModel.provideFactory(aiService) }
    )

    val scope = rememberCoroutineScope()
    val customAvatar by ProfileAvatarStore.avatarBitmap.collectAsStateWithLifecycle()
    val avatarPreviewState by imageGenViewModel.avatarPreviewState.collectAsStateWithLifecycle()

    val userProfile by mainViewModel.userProfile.collectAsStateWithLifecycle()
    val activeConversationId by mainViewModel.activeConversationId.collectAsStateWithLifecycle()
    val conversationsState by mainViewModel.conversationsState.collectAsStateWithLifecycle()
    val memoriesState by mainViewModel.memoriesState.collectAsStateWithLifecycle()
    val activeMessages by mainViewModel.activeMessages.collectAsStateWithLifecycle()
    val streamingReply by mainViewModel.streamingReply.collectAsStateWithLifecycle()
    val isAiResponding by mainViewModel.isAiResponding.collectAsStateWithLifecycle()
    val chatError by mainViewModel.chatError.collectAsStateWithLifecycle()
    val pendingMemory by mainViewModel.pendingMemorySuggestion.collectAsStateWithLifecycle()
    val statusMessage by mainViewModel.statusMessage.collectAsStateWithLifecycle()

    val language = AppLanguage.fromCode(userProfile?.preferredLanguage)
    val personality = AiPersonality.fromKey(userProfile?.selectedPersonality)
    val themeMode = AppThemeMode.fromKey(userProfile?.themeMode)
    val memoryEnabled = userProfile?.memoryEnabled ?: true
    val speechRate = (userProfile?.voiceSpeechRate ?: 1.0).toFloat()
    val autoSpeak = userProfile?.voiceAutoSpeak ?: true

    val strings = StringsProvider.get(language)
    var currentTab by rememberSaveable { mutableStateOf(SoulMateTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearStatusMessage()
        }
    }

    if (currentTab != SoulMateTab.HOME) {
        BackHandler {
            currentTab = SoulMateTab.HOME
        }
    }

    val navTabs = listOf(
        SoulMateTab.HOME to strings.home,
        SoulMateTab.CHAT to strings.chat,
        SoulMateTab.VOICE to strings.voice,
        SoulMateTab.STUDIO to strings.studio,
        SoulMateTab.MEMORY to strings.memory
    )

    SoulMateTheme(themeMode = themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(containerColor = NavySurface) {
                            navTabs.forEach { (tab, label) ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(containerColor = NavySurface) {
                            (navTabs + (SoulMateTab.SETTINGS to strings.settings)).forEach { (tab, label) ->
                                NavigationRailItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = label,
                                            tint = if (currentTab == tab) BrandCyan else androidx.compose.material3.LocalContentColor.current
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    when (currentTab) {
                        SoulMateTab.HOME -> HomeScreen(
                            strings = strings,
                            language = language,
                            personality = personality,
                            userProfile = userProfile,
                            customAvatar = customAvatar,
                            activeConversationId = activeConversationId,
                            conversationsState = conversationsState,
                            onToggleLanguage = {
                                val next = if (language == AppLanguage.ENGLISH) AppLanguage.BENGALI else AppLanguage.ENGLISH
                                mainViewModel.setLanguage(next)
                            },
                            onSelectPersonality = { mainViewModel.setPersonality(it) },
                            onStartNewChat = {
                                mainViewModel.startNewChat()
                                currentTab = SoulMateTab.CHAT
                            },
                            onOpenConversation = { id ->
                                mainViewModel.openConversation(id)
                                currentTab = SoulMateTab.CHAT
                            },
                            onRenameConversation = { id, newTitle ->
                                mainViewModel.renameConversation(id, newTitle)
                            },
                            onDeleteConversation = { id ->
                                mainViewModel.deleteConversation(id)
                            },
                            onOpenVisionChat = {
                                mainViewModel.startNewChat()
                                currentTab = SoulMateTab.CHAT
                            },
                            onOpenVoiceChat = { currentTab = SoulMateTab.VOICE },
                            onOpenImageStudio = { currentTab = SoulMateTab.STUDIO },
                            onOpenMemoryVault = { currentTab = SoulMateTab.MEMORY },
                            onOpenSettings = { currentTab = SoulMateTab.SETTINGS },
                            modifier = Modifier.weight(1f)
                        )

                        SoulMateTab.CHAT -> ChatScreen(
                            strings = strings,
                            language = language,
                            personality = personality,
                            activeConversationId = activeConversationId,
                            conversationsState = conversationsState,
                            messages = activeMessages,
                            streamingReply = streamingReply,
                            isAiResponding = isAiResponding,
                            chatError = chatError,
                            pendingMemorySuggestion = pendingMemory,
                            speechRate = speechRate,
                            onSelectPersonality = { mainViewModel.setPersonality(it) },
                            onSelectConversation = { id -> mainViewModel.openConversation(id) },
                            onRenameConversation = { id, newTitle ->
                                mainViewModel.renameConversation(id, newTitle)
                            },
                            onDeleteConversation = { id ->
                                mainViewModel.deleteConversation(id)
                            },
                            onNewChat = { mainViewModel.startNewChat() },
                            onSendMessage = { prompt, images -> mainViewModel.sendMessage(prompt, images) },
                            onStopGenerating = { mainViewModel.stopGenerating() },
                            onDismissError = { mainViewModel.clearChatError() },
                            onRequestSaveMemory = { mainViewModel.requestSaveMessageToMemory(it) },
                            onApproveMemory = { t, c, cat, sens ->
                                mainViewModel.approvePendingMemory(t, c, cat, sens)
                            },
                            onDismissMemorySuggestion = { mainViewModel.dismissPendingMemory() },
                            modifier = Modifier.weight(1f)
                        )

                        SoulMateTab.VOICE -> VoiceScreen(
                            strings = strings,
                            language = language,
                            personality = personality,
                            memoryContextPrompt = mainViewModel.buildActiveMemoryContext(),
                            speechRate = speechRate,
                            autoSpeak = autoSpeak,
                            voiceViewModel = voiceViewModel,
                            onToggleLanguage = {
                                val next = if (language == AppLanguage.ENGLISH) AppLanguage.BENGALI else AppLanguage.ENGLISH
                                mainViewModel.setLanguage(next)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        SoulMateTab.STUDIO -> ImageGenScreen(
                            strings = strings,
                            viewModel = imageGenViewModel,
                            onSetAsAvatar = { bitmap ->
                                scope.launch {
                                    ProfileAvatarStore.saveAvatar(context, bitmap)
                                        .onSuccess { snackbarHostState.showSnackbar(strings.avatarUpdated) }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        SoulMateTab.MEMORY -> MemoryScreen(
                            strings = strings,
                            memoryEnabled = memoryEnabled,
                            memoriesState = memoriesState,
                            onToggleMemoryEnabled = { mainViewModel.setMemoryEnabled(it) },
                            onAddMemory = { t, c, cat, sens -> mainViewModel.addMemory(t, c, cat, sens) },
                            onUpdateMemory = { id, t, c, cat, sens -> mainViewModel.updateMemory(id, t, c, cat, sens) },
                            onDeleteMemory = { mainViewModel.deleteMemory(it) },
                            modifier = Modifier.weight(1f)
                        )

                        SoulMateTab.SETTINGS -> SettingsScreen(
                            strings = strings,
                            userProfile = userProfile,
                            customAvatar = customAvatar,
                            avatarPreviewState = avatarPreviewState,
                            language = language,
                            themeMode = themeMode,
                            memoryEnabled = memoryEnabled,
                            voiceSpeechRate = speechRate,
                            voiceAutoSpeak = autoSpeak,
                            onGenerateAvatar = { desc, style ->
                                imageGenViewModel.generateAvatar(desc, style)
                            },
                            onResetAvatarPreview = {
                                imageGenViewModel.resetAvatarPreview()
                            },
                            onSaveCustomAvatar = { bitmap ->
                                scope.launch {
                                    ProfileAvatarStore.saveAvatar(context, bitmap)
                                        .onSuccess { snackbarHostState.showSnackbar(strings.avatarUpdated) }
                                }
                            },
                            onResetToUploadedPhoto = {
                                scope.launch {
                                    ProfileAvatarStore.clearAvatar(context)
                                        .onSuccess { snackbarHostState.showSnackbar(strings.avatarReset) }
                                }
                            },
                            onSetLanguage = { mainViewModel.setLanguage(it) },
                            onSetThemeMode = { mainViewModel.setThemeMode(it) },
                            onSetMemoryEnabled = { mainViewModel.setMemoryEnabled(it) },
                            onSetVoiceSpeechRate = { mainViewModel.setVoiceSpeechRate(it) },
                            onSetVoiceAutoSpeak = { mainViewModel.setVoiceAutoSpeak(it) },
                            onClearChatHistory = { mainViewModel.clearAllConversations() },
                            onClearAllMemories = { mainViewModel.clearAllMemories() },
                            onDeleteAllDataAndSignOut = {
                                scope.launch {
                                    ProfileAvatarStore.clearAvatar(context)
                                    mainViewModel.deleteAllDataAndSignOut {
                                        onSignOut()
                                    }
                                }
                            },
                            onSignOut = onSignOut,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
