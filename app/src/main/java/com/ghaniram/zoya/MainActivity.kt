package com.ghaniram.zoya

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.ghaniram.zoya.ui.theme.*
import com.ghaniram.zoya.ui.TimePickerWithPresetsDialog
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: ZoyaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { AnuSettingsStore.getInstance(this) }
            val settingsCount by store.stateVersion.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (store.themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> systemDark
            }
            ZoyaTheme(darkTheme = isDark) {
                AnuMainScreen(
                    viewModel = viewModel,
                    onOpenControlCenter = {
                        startActivity(Intent(this, ZoyaControlCenterActivity::class.java))
                    }
                )
            }
        }
    }
}

enum class AnuNavTab(val label: String, val iconSelected: ImageVector, val iconUnselected: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CHAT("Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    VISION("Vision", Icons.Filled.Visibility, Icons.Outlined.Visibility),
    TASKS("Tasks", Icons.Filled.AssignmentTurnedIn, Icons.Outlined.Checklist),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AnuMainScreen(
    viewModel: ZoyaViewModel,
    onOpenControlCenter: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var currentTab by rememberSaveable { mutableStateOf(AnuNavTab.HOME) }
    val context = LocalContext.current

    var micPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micPermissionGranted = it
        if (it) viewModel.connect()
    }

    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAnuColors.current.background)
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)) togetherWith
                                fadeOut(tween(180)) + scaleOut(targetScale = 0.98f, animationSpec = tween(180))
                    },
                    label = "TabNavAnimation"
                ) { tab ->
                    when (tab) {
                        AnuNavTab.HOME -> AnuHomeScreen(
                            state = state,
                            onOrbClick = {
                                if (!micPermissionGranted) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (state.connectionState == ConnectionState.DISCONNECTED) {
                                        viewModel.connect()
                                    } else {
                                        viewModel.disconnect()
                                    }
                                }
                            },
                            onSendPrompt = { prompt ->
                                viewModel.sendText(prompt)
                                currentTab = AnuNavTab.CHAT
                            },
                            onQuickAction = { action ->
                                when (action) {
                                    "call" -> {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                        }.onFailure {
                                            viewModel.sendText("Open Phone Dialer")
                                            currentTab = AnuNavTab.CHAT
                                        }
                                    }
                                    "message" -> {
                                        currentTab = AnuNavTab.CHAT
                                    }
                                    "reminder" -> {
                                        currentTab = AnuNavTab.TASKS
                                    }
                                    "control" -> {
                                        onOpenControlCenter()
                                    }
                                }
                            }
                        )
                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onVoiceClick = {
                                if (!micPermissionGranted) {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    if (state.connectionState == ConnectionState.DISCONNECTED) viewModel.connect()
                                    else viewModel.disconnect()
                                }
                            },
                            onClearChat = { viewModel.clearChatHistory() }
                        )
                        AnuNavTab.VISION -> AnuVisionTabScreen(
                            state = state,
                            onToggleVision = {
                                val next = !state.isVisionActive
                                viewModel.setVisionActive(next)
                                if (next) viewModel.startVisionSession()
                            },
                            onSendVisionPrompt = { prompt ->
                                viewModel.sendText(prompt)
                            },
                            onAnalyzeFrame = { bytes, prompt, callback ->
                                viewModel.analyzeVisionFrame(bytes, prompt, callback)
                            }
                        )
                        AnuNavTab.TASKS -> AnuTasksScreen(
                            tasks = state.tasks,
                            onToggleTask = { id -> viewModel.toggleTask(id) },
                            onDeleteTask = { id -> viewModel.deleteTask(id) },
                            onAddTask = { title, time -> viewModel.addTask(title, time) }
                        )
                        AnuNavTab.SETTINGS -> AnuSettingsScreen(
                            state = state,
                            onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                            onClearMemory = { viewModel.clearMemories() },
                            onOpenControlCenter = onOpenControlCenter
                        )
                    }
                }
            }

            // Bottom Navigation Bar matching the design exactly
            if (!isKeyboardVisible) {
                AnuBottomNavBar(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. HOME SCREEN
// -------------------------------------------------------------

@Composable
fun AnuHomeScreen(
    state: ZoyaUiState,
    onOrbClick: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onQuickAction: (String) -> Unit
) {
    var searchInput by remember { mutableStateOf("") }
    val isConnected = state.connectionState != ConnectionState.DISCONNECTED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: Greeting & Subtitle
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Good morning, Ghaniram 👋",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnuTextDark,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "I'm Anu. What can I help you with?",
                    fontSize = 13.5.sp,
                    color = AnuTextMuted,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Center Glowing Assistant Orb
        item {
            Spacer(Modifier.height(10.dp))
            AnuCenterAssistantOrb(
                connectionState = state.connectionState,
                inputLevel = state.inputLevel,
                outputLevel = state.outputLevel,
                onClick = onOrbClick
            )
            Spacer(Modifier.height(8.dp))
            // Status: Green dot + Status text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) {
                                when (state.connectionState) {
                                    ConnectionState.LISTENING -> AnuPrimary
                                    ConnectionState.SPEAKING -> AnuSecondary
                                    ConnectionState.CONNECTING -> Color(0xFFF59E0B)
                                    else -> AnuStatusGreen
                                }
                            } else AnuStatusGreen
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (state.connectionState) {
                        ConnectionState.LISTENING -> "Listening to you..."
                        ConnectionState.SPEAKING -> "Anu is speaking..."
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.IDLE -> "Ready to help"
                        ConnectionState.DISCONNECTED -> "Ready to help"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnuTextDark
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        // Search / Input Pill: "Ask Anu anything..."
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0x106C38FF),
                        spotColor = Color(0x156C38FF)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchInput.isEmpty()) {
                            Text(
                                text = "Ask Anu anything...",
                                fontSize = 13.5.sp,
                                color = AnuTextMuted
                            )
                        }
                        BasicTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            textStyle = TextStyle(
                                fontSize = 13.5.sp,
                                color = AnuTextDark,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(AnuPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (searchInput.isNotBlank()) {
                                    onSendPrompt(searchInput)
                                    searchInput = ""
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Circular Purple Mic / Send Button
                    val isActionSend = searchInput.isNotBlank()
                    val buttonInteractionSource = remember { MutableInteractionSource() }
                    val buttonPressed by buttonInteractionSource.collectIsPressedAsState()
                    val buttonScale by animateFloatAsState(
                        targetValue = if (buttonPressed) 0.90f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "micScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(buttonScale)
                            .clip(CircleShape)
                            .background(AnuPrimary)
                            .clickable(
                                interactionSource = buttonInteractionSource,
                                indication = rememberRipple(color = Color.White),
                                onClick = {
                                    if (isActionSend) {
                                        onSendPrompt(searchInput)
                                        searchInput = ""
                                    } else {
                                        onOrbClick()
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActionSend) Icons.Filled.Send else Icons.Filled.Mic,
                            contentDescription = if (isActionSend) "Send" else "Microphone",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // Quick Actions Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = "Quick actions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnuTextDark,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 2x2 Grid of Actions matching the image exactly: Call, Message, Reminder, Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Outlined.Call,
                        title = "Call",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("call") }
                    )
                    QuickActionCard(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Message",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("message") }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard(
                        icon = Icons.Outlined.Notifications,
                        title = "Reminder",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("reminder") }
                    )
                    QuickActionCard(
                        icon = Icons.Outlined.Tune,
                        title = "Control",
                        modifier = Modifier.weight(1f),
                        onClick = { onQuickAction("control") }
                    )
                }
            }
        }
    }
}

@Composable
fun AnuCenterAssistantOrb(
    connectionState: ConnectionState,
    inputLevel: Float,
    outputLevel: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressOrb"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
    val breathingGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingGlow"
    )

    val audioBoost = (inputLevel + outputLevel).coerceIn(0f, 1f) * 0.25f
    val dynamicOuterScale = breathingGlow + audioBoost

    Box(
        modifier = Modifier
            .size(240.dp)
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Outer soft glow halo
        Box(
            modifier = Modifier
                .size(220.dp * dynamicOuterScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AnuAccent.copy(alpha = 0.28f),
                            AnuSecondary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Middle glowing ring
        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AnuSecondary.copy(alpha = 0.45f),
                            AnuPrimary.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Core Solid Orb
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .size(136.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = AnuPrimary,
                    spotColor = AnuPrimary
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF9D5CFF),
                                Color(0xFF6C38FF),
                                Color(0xFF4C1D95)
                            ),
                            center = Offset(68f, 68f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ANU",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.5.sp
                )
            }
        }
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AnuCardSurface,
        border = BorderStroke(1.dp, AnuBorder),
        modifier = modifier
            .height(58.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = AnuPrimary.copy(alpha = 0.2f)),
                onClick = onClick
            )
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x086C38FF),
                spotColor = Color(0x0C6C38FF)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AnuPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnuTextDark
            )
        }
    }
}

// -------------------------------------------------------------
// 2. CHAT WITH ANU SCREEN
// -------------------------------------------------------------

@Composable
fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showChatHistoryDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val displayMessages = state.chatMessages
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val context = LocalContext.current

    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.lastIndex)
        }
    }

    LaunchedEffect(displayMessages.size, state.isAnuResponding) {
        if (displayMessages.isNotEmpty() || state.isAnuResponding) {
            val target = if (state.isAnuResponding) displayMessages.size else (displayMessages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    val timeFormatter = remember {
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AnuPrimary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Anu Assistant Chat",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnuTextDark,
                        letterSpacing = (-0.2).sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Room-persisted private memory & conversation",
                    fontSize = 11.5.sp,
                    color = AnuTextMuted
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, "More", tint = AnuTextDark)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(AnuCardSurface)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Chat History (ଚାଟ୍ ଇତିହାସ)",
                                color = AnuTextDark,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            showMenu = false
                            showChatHistoryDialog = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.History, null, tint = AnuPrimary) }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Export / Share Chat",
                                color = AnuTextDark,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            showMenu = false
                            if (displayMessages.isNotEmpty()) {
                                val fullText = displayMessages.joinToString("\n\n") { msg ->
                                    val who = if (msg.role == ChatRole.USER) "You" else "Anu"
                                    val time = runCatching { timeFormatter.format(Date(msg.timestampMillis)) }.getOrDefault("")
                                    "[$who - $time]\n${msg.text}"
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Anu Chat History")
                                    putExtra(Intent.EXTRA_TEXT, fullText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Chat History"))
                            }
                        },
                        leadingIcon = { Icon(Icons.Outlined.Share, null, tint = AnuTextDark) }
                    )
                    HorizontalDivider(color = AnuBorder.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = { Text("Clear Chat History", color = Color(0xFFDC2626), fontWeight = FontWeight.Medium) },
                        onClick = {
                            showMenu = false
                            showClearConfirmDialog = true
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFDC2626)) }
                    )
                }
            }
        }

        // Chat Message List or Empty Placeholder
        if (displayMessages.isEmpty() && !state.isAnuResponding) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = AnuLavenderBg,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Psychology,
                                contentDescription = null,
                                tint = AnuPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Talk with Anu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AnuTextDark
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Messages and memory are stored locally. Your conversation with Anu is completely private.",
                        fontSize = 13.sp,
                        color = AnuTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AnuCardSurface,
                            border = BorderStroke(1.dp, AnuBorder),
                            modifier = Modifier.clickable { onSendMessage("Hello Anu! କେମିତି ଅଛୁ?") }
                        ) {
                            Text("👋 Say Hello", fontSize = 12.sp, color = AnuPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AnuCardSurface,
                            border = BorderStroke(1.dp, AnuBorder),
                            modifier = Modifier.clickable { onSendMessage("Remember my notes") }
                        ) {
                            Text("🧠 Remember", fontSize = 12.sp, color = AnuPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(displayMessages, key = { it.id }) { msg ->
                    val isUser = msg.role == ChatRole.USER
                    val cleanText = msg.text.removePrefix("You:").removePrefix("You :").trim()
                    val timeString = remember(msg.timestampMillis) {
                        timeFormatter.format(java.util.Date(msg.timestampMillis))
                    }

                    if (isUser) {
                        // User message: right-aligned with "You" badge and distinct primary styling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = "You",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AnuPrimary,
                                    modifier = Modifier.padding(end = 6.dp, bottom = 3.dp)
                                )
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = 18.dp,
                                        bottomEnd = 4.dp
                                    ),
                                    color = AnuPrimary,
                                    modifier = Modifier.shadow(
                                        elevation = 2.dp,
                                        shape = RoundedCornerShape(18.dp),
                                        ambientColor = Color(0x206C38FF),
                                        spotColor = Color(0x306C38FF)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = cleanText,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.align(Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = timeString,
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "✓✓",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            // User avatar
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AnuLavenderBg)
                                    .border(1.dp, AnuBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "User",
                                    tint = AnuPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // Anu message: left-aligned with "Anu ✨" badge and distinct surface card styling
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Anu avatar
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(AnuPrimary, AnuSecondary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Anu AI",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.widthIn(max = 285.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                                ) {
                                    Text(
                                        text = "Anu",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AnuTextDark
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "AI",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AnuPrimary,
                                        modifier = Modifier
                                            .background(AnuLavenderBg, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 18.dp
                                    ),
                                    color = AnuCardSurface,
                                    border = BorderStroke(1.dp, AnuBorder),
                                    modifier = Modifier.shadow(
                                        elevation = 1.5.dp,
                                        shape = RoundedCornerShape(18.dp),
                                        ambientColor = Color(0x10000000),
                                        spotColor = Color(0x15000000)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = cleanText,
                                            fontSize = 14.sp,
                                            color = AnuTextDark,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = timeString,
                                            fontSize = 10.sp,
                                            color = AnuTextMuted,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isAnuResponding) {
                    item(key = "anu_typing_bubble") {
                        AnuTypingIndicatorBubble()
                    }
                }
            }
        }

        // Bottom Input Row: Input Pill + Mic Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (messageText.isEmpty()) {
                            Text(
                                text = "Message Anu...",
                                fontSize = 13.5.sp,
                                color = AnuTextMuted
                            )
                        }
                        BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            textStyle = TextStyle(
                                fontSize = 13.5.sp,
                                color = AnuTextDark
                            ),
                            cursorBrush = SolidColor(AnuPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (messageText.isNotBlank()) {
                        IconButton(
                            onClick = { messageText = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = AnuTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AnuPrimary)
                            .clickable {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // Separate Circular Mic Button
            Surface(
                shape = CircleShape,
                color = AnuLavenderBg,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .size(52.dp)
                    .clickable(onClick = onVoiceClick)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice",
                        tint = AnuPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    if (showChatHistoryDialog) {
        AnuChatHistoryDialog(
            messages = state.chatMessages,
            onDismiss = { showChatHistoryDialog = false },
            onClear = {
                onClearChat()
                showChatHistoryDialog = false
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear Chat History?",
                    fontWeight = FontWeight.Bold,
                    color = AnuTextDark
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently clear all messages from this conversation? This will also remove them from local memory.",
                    color = AnuTextMuted,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearChat()
                    }
                ) {
                    Text("Clear All", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = AnuTextDark)
                }
            },
            containerColor = AnuCardSurface
        )
    }
}

@Composable
fun AnuTypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "AnuTypingPulse")

    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AnuPrimary, AnuSecondary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Anu AI",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
            ) {
                Text(
                    text = "Anu",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnuPrimary
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AnuLavenderBg,
                    border = BorderStroke(0.5.dp, AnuBorder)
                ) {
                    Text(
                        text = "Thinking (ଭାବୁଛନ୍ତି...)",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AnuPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomEnd = 16.dp,
                    bottomStart = 16.dp
                ),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                shadowElevation = 0.5.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = dot1Offset.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AnuPrimary)
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot2Offset.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AnuPrimary.copy(alpha = 0.85f))
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = dot3Offset.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AnuPrimary.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Anu is preparing response...",
                        fontSize = 12.sp,
                        color = AnuTextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var copiedMsgId by remember { mutableStateOf<String?>(null) }
    var showConfirmClear by remember { mutableStateOf(false) }

    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    val timeFormatter = remember {
        SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AnuLavenderBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = AnuPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Chat History (ଚାଟ୍ ଇତିହାସ)",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AnuTextDark
                                )
                                Text(
                                    text = "${messages.size} total messages in memory",
                                    fontSize = 11.5.sp,
                                    color = AnuTextMuted
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = AnuTextMuted)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Search box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AnuBackground,
                        border = BorderStroke(1.dp, AnuBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = AnuTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text("Search conversation...", fontSize = 13.sp, color = AnuTextMuted)
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    textStyle = TextStyle(fontSize = 13.sp, color = AnuTextDark),
                                    singleLine = true,
                                    cursorBrush = SolidColor(AnuPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Filled.Close, "Clear", tint = AnuTextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (filteredMessages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "No conversation history yet." else "No messages match '$searchQuery'",
                                fontSize = 13.sp,
                                color = AnuTextMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(filteredMessages, key = { it.id }) { msg ->
                                val isUser = msg.role == ChatRole.USER
                                val clean = msg.text.removePrefix("You:").removePrefix("You :").trim()
                                val time = runCatching { timeFormatter.format(Date(msg.timestampMillis)) }.getOrDefault("")

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isUser) AnuLavenderBg.copy(alpha = 0.6f) else AnuBackground,
                                    border = BorderStroke(0.5.dp, AnuBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isUser) AnuPrimary else AnuSecondary
                                                ) {
                                                    Text(
                                                        text = if (isUser) "You" else "Anu ✨",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text(text = time, fontSize = 10.sp, color = AnuTextMuted)
                                            }

                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(clean))
                                                    copiedMsgId = msg.id
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (copiedMsgId == msg.id) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = if (copiedMsgId == msg.id) AnuStatusGreen else AnuTextMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = clean,
                                            fontSize = 13.sp,
                                            color = AnuTextDark,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Bottom actions: Share & Clear
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (messages.isNotEmpty()) {
                                    val full = messages.joinToString("\n\n") { msg ->
                                        val who = if (msg.role == ChatRole.USER) "You" else "Anu"
                                        val time = runCatching { timeFormatter.format(Date(msg.timestampMillis)) }.getOrDefault("")
                                        "[$who - $time]\n${msg.text}"
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Anu Chat History")
                                        putExtra(Intent.EXTRA_TEXT, full)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Chat History"))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AnuBorder)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = AnuPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Export", fontSize = 12.5.sp, color = AnuPrimary)
                        }

                        Button(
                            onClick = { showConfirmClear = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2))
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFDC2626))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear All", fontSize = 12.5.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("Clear Chat History?", color = AnuTextDark, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently clear all messages from private local storage? This cannot be undone.", color = AnuTextMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmClear = false
                        onClear()
                    }
                ) {
                    Text("Clear", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text("Cancel", color = AnuTextDark)
                }
            },
            containerColor = AnuCardSurface
        )
    }
}

// -------------------------------------------------------------
// 3. VISION SCREEN
// -------------------------------------------------------------

@Composable
fun AnuDeskScene(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Warm room wall gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8E2D9),
                        Color(0xFFDDD5C8),
                        Color(0xFFC7BBAE)
                    ),
                    startY = 0f,
                    endY = h * 0.58f
                ),
                size = Size(w, h * 0.58f)
            )

            // 2. Wooden desk surface with perspective
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFA67B54),
                        Color(0xFF8F633E),
                        Color(0xFF754D2B)
                    ),
                    startY = h * 0.58f,
                    endY = h
                ),
                topLeft = Offset(0f, h * 0.58f),
                size = Size(w, h * 0.42f)
            )
            // Subtle desk edge line
            drawLine(
                color = Color(0xFF634123),
                start = Offset(0f, h * 0.58f),
                end = Offset(w, h * 0.58f),
                strokeWidth = 2.dp.toPx()
            )

            // 3. Plant on the Left
            val potLeft = w * 0.08f
            val potTop = h * 0.48f
            val potWidth = w * 0.22f
            val potHeight = h * 0.22f
            drawRoundRect(
                color = Color(0xFFF1EFEA),
                topLeft = Offset(potLeft, potTop),
                size = Size(potWidth, potHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
            )
            // Plant leaves
            val leafGreenDark = Color(0xFF1B5E20)
            val leafGreenMed = Color(0xFF2E7D32)
            val leafGreenLight = Color(0xFF4CAF50)
            val leafGreenBright = Color(0xFF81C784)

            val plantCenterX = potLeft + potWidth * 0.5f
            val plantTopY = potTop
            drawOval(leafGreenDark, Offset(plantCenterX - 35.dp.toPx(), plantTopY - 70.dp.toPx()), Size(45.dp.toPx(), 75.dp.toPx()))
            drawOval(leafGreenMed, Offset(plantCenterX - 55.dp.toPx(), plantTopY - 45.dp.toPx()), Size(50.dp.toPx(), 65.dp.toPx()))
            drawOval(leafGreenLight, Offset(plantCenterX - 15.dp.toPx(), plantTopY - 85.dp.toPx()), Size(42.dp.toPx(), 80.dp.toPx()))
            drawOval(leafGreenMed, Offset(plantCenterX + 5.dp.toPx(), plantTopY - 60.dp.toPx()), Size(48.dp.toPx(), 70.dp.toPx()))
            drawOval(leafGreenBright, Offset(plantCenterX - 25.dp.toPx(), plantTopY - 50.dp.toPx()), Size(35.dp.toPx(), 55.dp.toPx()))

            // 4. Center Black Mug with "ANU"
            val mugCenterX = w * 0.49f
            val mugCenterY = h * 0.62f
            val mugW = w * 0.28f
            val mugH = h * 0.26f
            val mugLeft = mugCenterX - mugW * 0.5f
            val mugTop = mugCenterY - mugH * 0.5f

            // Mug shadow on table
            drawOval(
                color = Color(0x55000000),
                topLeft = Offset(mugLeft - 10.dp.toPx(), mugTop + mugH - 8.dp.toPx()),
                size = Size(mugW + 20.dp.toPx(), 18.dp.toPx())
            )

            // Mug handle on left
            val handleW = 20.dp.toPx()
            val handleH = mugH * 0.55f
            drawRoundRect(
                color = Color(0xFF1E2024),
                topLeft = Offset(mugLeft - handleW * 0.75f, mugTop + mugH * 0.22f),
                size = Size(handleW, handleH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                style = Stroke(width = 6.dp.toPx())
            )

            // Mug body
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF2B2E35),
                        Color(0xFF1C1E22),
                        Color(0xFF141518)
                    ),
                    startX = mugLeft,
                    endX = mugLeft + mugW
                ),
                topLeft = Offset(mugLeft, mugTop),
                size = Size(mugW, mugH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
            )

            // 5. Laptop on the Right
            val laptopLeft = w * 0.67f
            val laptopTop = h * 0.35f
            val laptopW = w * 0.38f
            val laptopH = h * 0.45f

            // Laptop screen
            drawRoundRect(
                color = Color(0xFF1E2128),
                topLeft = Offset(laptopLeft + 15.dp.toPx(), laptopTop),
                size = Size(laptopW * 0.82f, laptopH * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
            // Screen display area
            drawRoundRect(
                color = Color(0xFF12151B),
                topLeft = Offset(laptopLeft + 19.dp.toPx(), laptopTop + 4.dp.toPx()),
                size = Size(laptopW * 0.82f - 8.dp.toPx(), laptopH * 0.62f - 8.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
            )
            // Keyboard base in perspective
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2A2E38), Color(0xFF1E2128)),
                    startY = laptopTop + laptopH * 0.6f,
                    endY = laptopTop + laptopH * 0.85f
                ),
                topLeft = Offset(laptopLeft, laptopTop + laptopH * 0.6f),
                size = Size(laptopW, laptopH * 0.25f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
        }

        // Bold white "ANU" printed text on the center mug
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(x = (-2).dp, y = 42.dp)
            ) {
                Text(
                    text = "ANU",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.95f),
                    letterSpacing = 1.8.sp
                )
            }
        }
    }
}

@Composable
fun AnuVisionTabScreen(
    state: ZoyaUiState,
    onToggleVision: () -> Unit,
    onSendVisionPrompt: (String) -> Unit,
    onAnalyzeFrame: (ByteArray, String, ((String) -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraPermissionGranted = it
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var autoSightEnabled by remember { mutableStateOf(false) }
    var visionPrompt by remember { mutableStateOf("") }
    var lastObservationTime by remember { mutableStateOf("") }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    fun captureAndAnalyze(promptText: String) {
        val pv = previewViewRef ?: return
        val bmp = pv.bitmap ?: return
        isAnalyzing = true
        val stream = ByteArrayOutputStream()
        val maxDim = 1024
        val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        val bytes = stream.toByteArray()
        onAnalyzeFrame(bytes, promptText) {
            isAnalyzing = false
            lastObservationTime = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date())
        }
    }

    // Auto-Sight continuous observation loop
    LaunchedEffect(autoSightEnabled, state.isVisionActive, cameraPermissionGranted) {
        if (autoSightEnabled && state.isVisionActive && cameraPermissionGranted) {
            while (isActive && autoSightEnabled && state.isVisionActive) {
                delay(4500)
                if (!isAnalyzing && previewViewRef?.bitmap != null) {
                    captureAndAnalyze("Briefly describe what is directly in front of the camera in 1-2 natural sentences.")
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderRef.value?.unbindAll()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.isVisionActive) AnuStatusGreen else Color(0xFFF59E0B))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Anu Real-World Vision",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnuTextDark,
                        letterSpacing = (-0.2).sp
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Live camera perception powered by Gemini 3.6 Flash",
                    fontSize = 11.5.sp,
                    color = AnuTextMuted
                )
            }

            // Auto-Sight toggle chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (autoSightEnabled) AnuPrimary else AnuLavenderBg,
                border = BorderStroke(1.dp, if (autoSightEnabled) AnuPrimary else AnuBorder),
                modifier = Modifier.clickable {
                    if (!cameraPermissionGranted) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        autoSightEnabled = !autoSightEnabled
                        if (autoSightEnabled && !state.isVisionActive) {
                            onToggleVision()
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (autoSightEnabled) Icons.Filled.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = if (autoSightEnabled) Color.White else AnuPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (autoSightEnabled) "Auto-Sight ON" else "Auto-Sight",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (autoSightEnabled) Color.White else AnuPrimary
                    )
                }
            }
        }

        // Live Vision Camera Viewport Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(BorderStroke(1.dp, AnuBorder), RoundedCornerShape(22.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (cameraPermissionGranted && state.isVisionActive) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewViewRef = this
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    cameraProviderRef.value = cameraProvider
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }
                                    val cameraSelector = CameraSelector.Builder()
                                        .requireLensFacing(lensFacing)
                                        .build()
                                    cameraProvider.unbindAll()
                                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                    cameraControlRef = camera.cameraControl
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    update = {
                        previewViewRef = it
                    },
                    onRelease = {
                        try {
                            cameraProviderRef.value?.unbindAll()
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls Overlay over camera viewfinder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "● LIVE EYES" badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xCC0F172A),
                        border = BorderStroke(1.dp, Color(0x40FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "LIVE CAMERA",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Camera tools: Lens Flip & Torch
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Torch button
                        Surface(
                            shape = CircleShape,
                            color = if (isTorchOn) AnuPrimary else Color(0xCC0F172A),
                            border = BorderStroke(1.dp, Color(0x40FFFFFF)),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    val next = !isTorchOn
                                    isTorchOn = next
                                    cameraControlRef?.enableTorch(next)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Outlined.FlashOff,
                                    contentDescription = "Flashlight",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Switch Camera Lens button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0F172A),
                            border = BorderStroke(1.dp, Color(0x40FFFFFF)),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                    try {
                                        val cameraProvider = cameraProviderRef.value
                                        if (cameraProvider != null) {
                                            val preview = Preview.Builder().build().also {
                                                previewViewRef?.surfaceProvider?.let { sp -> it.setSurfaceProvider(sp) }
                                            }
                                            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                                            cameraProvider.unbindAll()
                                            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                                            cameraControlRef = camera.cameraControl
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.FlipCameraAndroid,
                                    contentDescription = "Switch Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                // Viewfinder Reticle / Corner Brackets
                Canvas(modifier = Modifier.size(200.dp, 160.dp)) {
                    val strokeW = 3.dp.toPx()
                    val bracketLen = 24.dp.toPx()
                    val bracketColor = Color(0xCC6C38FF)

                    // Top-Left bracket
                    drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW)
                    drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bracketLen), strokeW)

                    // Top-Right bracket
                    drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), strokeW)
                    drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, bracketLen), strokeW)

                    // Bottom-Left bracket
                    drawLine(bracketColor, Offset(0f, size.height), Offset(bracketLen, size.height), strokeW)
                    drawLine(bracketColor, Offset(0f, size.height), Offset(0f, size.height - bracketLen), strokeW)

                    // Bottom-Right bracket
                    drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), strokeW)
                    drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), strokeW)
                }

                // Laser Scanning Line Animation when analyzing
                if (isAnalyzing) {
                    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
                    val scanProgress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scanLine"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * scanProgress
                        drawLine(
                            color = Color(0xFF6C38FF),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 3.dp.toPx()
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEE0F172A),
                        border = BorderStroke(1.dp, AnuPrimary),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AnuPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Anu is looking with her eyes...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Floating Look Button at the bottom of the camera card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AnuPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clickable {
                            captureAndAnalyze("Describe what is directly in front of the camera in detail as if you are looking with your own eyes.")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Camera,
                            contentDescription = "Look Now",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "📸 Look with Anu's Eyes (ଏବେ ଦେଖନ୍ତୁ)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else if (!cameraPermissionGranted) {
                // Camera Permission Needed state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x336C38FF),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = AnuSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Camera Access Needed",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "To allow Anu to see the real world with her own eyes, grant camera permission.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = AnuPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Grant Camera Permission", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Camera Standby state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x226C38FF),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = AnuSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Anu Vision is in Standby",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tap 'Start Vision' to open the live camera and let Anu look at your real surroundings.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { onToggleVision() },
                        colors = ButtonDefaults.buttonColors(containerColor = AnuPrimary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("▶ Open Anu's Eyes", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Observation Card: "Anu is seeing..."
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AnuCardSurface,
            border = BorderStroke(1.dp, AnuBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = AnuPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Anu is seeing...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnuPrimary
                        )
                    }

                    if (lastObservationTime.isNotBlank()) {
                        Text(
                            text = lastObservationTime,
                            fontSize = 11.sp,
                            color = AnuTextMuted
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val descriptionText = when {
                    isAnalyzing -> "Looking closely through the camera and analyzing the real world with Gemini..."
                    state.visionDescription.isNotBlank() -> state.visionDescription
                    state.isVisionActive -> "Camera is active! Point at any object, document, or room and tap 'Look with Anu's Eyes' below."
                    else -> "Start vision to let Anu look at your surroundings with her own eyes."
                }
                Text(
                    text = descriptionText,
                    fontSize = 14.sp,
                    color = AnuTextDark,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(12.dp))

                // Waveform bars
                AnuWaveformVisualizer(active = state.isVisionActive)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Quick Action Chips
        Text(
            text = "Ask Anu about what she sees:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = AnuTextMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        captureAndAnalyze("What objects and items do you see in front of the camera? Name and describe them.")
                    }
            ) {
                Text(
                    text = "📦 What is this?",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnuPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        captureAndAnalyze("Read all visible text, letters, or numbers in the camera frame clearly (OCR).")
                    }
            ) {
                Text(
                    text = "📝 Read text (OCR)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnuPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AnuCardSurface,
                border = BorderStroke(1.dp, AnuBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        captureAndAnalyze("Describe the surrounding environment, lighting, room, and scene in detail.")
                    }
            ) {
                Text(
                    text = "🏠 Describe scene",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnuPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Input Pill: "Ask Anu about what you see..."
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = AnuCardSurface,
            border = BorderStroke(1.dp, AnuBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (visionPrompt.isEmpty()) {
                        Text(
                            text = "Ask Anu about what you see...",
                            fontSize = 13.sp,
                            color = AnuTextMuted
                        )
                    }
                    BasicTextField(
                        value = visionPrompt,
                        onValueChange = { visionPrompt = it },
                        textStyle = TextStyle(fontSize = 13.sp, color = AnuTextDark),
                        singleLine = true,
                        cursorBrush = SolidColor(AnuPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (visionPrompt.isNotBlank()) {
                                val p = visionPrompt
                                visionPrompt = ""
                                captureAndAnalyze(p)
                            }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AnuPrimary)
                        .clickable {
                            if (visionPrompt.isNotBlank()) {
                                val p = visionPrompt
                                visionPrompt = ""
                                captureAndAnalyze(p)
                            } else {
                                captureAndAnalyze("Describe what is in front of the camera in detail.")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (visionPrompt.isNotBlank()) Icons.Filled.Send else Icons.Filled.Camera,
                        contentDescription = "Action",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Full-Width Purple Action Button: "■ Stop vision" / "▶ Start vision"
        val isVisionOn = state.isVisionActive
        Button(
            onClick = {
                if (!isVisionOn && !cameraPermissionGranted) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                onToggleVision()
            },
            colors = ButtonDefaults.buttonColors(containerColor = AnuPrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = if (isVisionOn) "■  Stop vision (କ୍ୟାମେରା ବନ୍ଦ)" else "▶  Start vision (ଆଖି ଖୋଲନ୍ତୁ)",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AnuWaveformVisualizer(active: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barsCount = 32
        for (i in 0 until barsCount) {
            val factor = if (active) {
                abs(sin(phase + i * 0.35f)) * 0.8f + 0.2f
            } else {
                abs(sin(i * 0.45f)) * 0.4f + 0.2f
            }
            val height = (factor * 26).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i % 3 == 0) AnuSecondary else AnuPrimary)
            )
        }
    }
}

// -------------------------------------------------------------
// 4. TASKS SCREEN
// -------------------------------------------------------------

@Composable
fun AnuTasksScreen(
    tasks: List<AnuTask>,
    onToggleTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onAddTask: (String, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskTime by remember { mutableStateOf("7:00 PM") }
    var showMenu by remember { mutableStateOf(false) }

    val remainingCount = tasks.count { !it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tasks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnuTextDark,
                    letterSpacing = (-0.2).sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Your reminders and scheduled actions.",
                    fontSize = 12.sp,
                    color = AnuTextMuted
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, "More", tint = AnuTextDark)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(AnuCardSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add new task", color = AnuTextDark) },
                        onClick = {
                            showMenu = false
                            showAddDialog = true
                        }
                    )
                }
            }
        }

        // Featured Purple Gradient Banner Card: "Today / X tasks planned"
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = AnuPrimary,
                    spotColor = AnuPrimary
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF6C38FF),
                                Color(0xFF9D5CFF)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Event,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Today",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$remainingCount tasks planned",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tasks List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AnuCardSurface,
                    border = BorderStroke(1.dp, AnuBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleTask(task.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Interactive Circle Checkbox
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = if (task.isCompleted) AnuPrimary else Color(0xFF94A3B8),
                                    shape = CircleShape
                                )
                                .background(if (task.isCompleted) AnuPrimary else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isCompleted) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Completed",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        // Task Title & Time
                        Text(
                            text = "${task.title} • ${task.timeLabel}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (task.isCompleted) AnuTextMuted else AnuTextDark,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onDeleteTask(task.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Delete",
                                tint = AnuTextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Button: "+ Add new task"
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = AnuPrimary),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(48.dp)
        ) {
            Text(
                text = "+  Add new task",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Task", fontWeight = FontWeight.Bold, color = AnuTextDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    var showTimePickerDialog by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = newTaskTime,
                        onValueChange = { newTaskTime = it },
                        label = { Text("Time (e.g. 7:00 PM)") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showTimePickerDialog = true }) {
                                Icon(
                                    Icons.Filled.Schedule,
                                    contentDescription = "Pick Time",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Quick Presets:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15 to "+15m", 30 to "+30m", 60 to "+1h").forEach { (min, label) ->
                            AssistChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, min) }
                                    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                                    val h = if (cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
                                    newTaskTime = String.format(Locale.US, "%d:%02d %s", h, cal.get(Calendar.MINUTE), ampm)
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                        AssistChip(
                            onClick = { showTimePickerDialog = true },
                            label = { Text("🕒 Clock", fontSize = 11.sp) }
                        )
                    }
                    if (showTimePickerDialog) {
                        TimePickerWithPresetsDialog(
                            onConfirm = { hour, minute ->
                                val ampm = if (hour >= 12) "PM" else "AM"
                                val h12 = if (hour % 12 == 0) 12 else hour % 12
                                newTaskTime = String.format(Locale.US, "%d:%02d %s", h12, minute, ampm)
                                showTimePickerDialog = false
                            },
                            onDismiss = { showTimePickerDialog = false }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            onAddTask(newTaskTitle, newTaskTime)
                            newTaskTitle = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AnuPrimary)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = AnuTextMuted)
                }
            },
            containerColor = AnuCardSurface
        )
    }
}

// -------------------------------------------------------------
// 5. SETTINGS SCREEN
// -------------------------------------------------------------

@Composable
fun AnuSettingsScreen(
    state: ZoyaUiState,
    onSelectLanguage: (ZoyaLanguage) -> Unit,
    onClearMemory: () -> Unit,
    onOpenControlCenter: () -> Unit
) {
    com.ghaniram.zoya.settings.AnuSettingsContainerScreen(
        state = state,
        onSelectLanguage = onSelectLanguage,
        onClearMemory = onClearMemory,
        onOpenControlCenter = onOpenControlCenter
    )
}

@Composable
fun SettingsMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AnuCardSurface,
        border = BorderStroke(1.dp, AnuBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AnuLavenderBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AnuPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnuTextDark
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = AnuTextMuted
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = AnuTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// BOTTOM NAVIGATION BAR
// -------------------------------------------------------------

@Composable
fun AnuBottomNavBar(
    selectedTab: AnuNavTab,
    onTabSelected: (AnuNavTab) -> Unit
) {
    val colors = LocalAnuColors.current
    Surface(
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnuNavTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val icon = if (isSelected) tab.iconSelected else tab.iconUnselected
                val color = if (isSelected) colors.accentPrimary else colors.textSecondary

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = color
                    )
                }
            }
        }
    }
}
