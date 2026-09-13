from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Required source anchor not found: {label}")
    return text.replace(old, new, 1)

# 1. AndroidManifest.xml: package visibility for querying and opening apps
p_manifest = ROOT / "app/src/main/AndroidManifest.xml"
if p_manifest.exists():
    s = p_manifest.read_text(encoding="utf-8")
    if "QUERY_ALL_PACKAGES" not in s:
        s = s.replace(
            '<uses-permission android:name="android.permission.INTERNET"',
            '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />\n    <uses-permission android:name="android.permission.INTERNET"',
            1
        )
    if "<queries>" not in s:
        queries_block = """    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
        </intent>
    </queries>\n"""
        s = s.replace("    <application", queries_block + "    <application", 1)
    p_manifest.write_text(s, encoding="utf-8")
    print("Manifest package visibility updated")

# 2. AccessibilityControlService.kt: screen-level scroll gesture fallback
p_acc = PKG / "AccessibilityControlService.kt"
if p_acc.exists():
    s = p_acc.read_text(encoding="utf-8")
    if "performScreenScrollGesture" not in s:
        helper = """    private fun performScreenScrollGesture(forward: Boolean): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val display = resources.displayMetrics
        val width = display.widthPixels
        val height = display.heightPixels
        val x = width * 0.5f
        val startY = if (forward) height * 0.75f else height * 0.25f
        val endY = if (forward) height * 0.25f else height * 0.75f
        val path = android.graphics.Path().apply { moveTo(x, startY); lineTo(x, endY) }
        return dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 300L)).build(), null, null)
    }
"""
        s = s.replace("    private fun waitForUiSettle", helper + "    private fun waitForUiSettle", 1)
        old_scroll = "if (attempt < 2) waitForUiSettle(120L + attempt * 100L) }; return false }"
        new_scroll = "if (!acted) { acted = performScreenScrollGesture(forward); if (acted) { waitForUiSettle(180L); return true } }; if (attempt < 2) waitForUiSettle(120L + attempt * 100L) }; return performScreenScrollGesture(forward) }"
        if old_scroll in s:
            s = s.replace(old_scroll, new_scroll, 1)
        p_acc.write_text(s, encoding="utf-8")
        print("AccessibilityControlService scroll fallback updated")

# 3. PhoneControlManager.kt: goHome fallback, enhanced scroll, and system controls
p_phone = PKG / "PhoneControlManager.kt"
if p_phone.exists():
    s = p_phone.read_text(encoding="utf-8")
    if "fun goHome(): String" not in s:
        go_home_code = """    fun goHome(): String {
        val service = AccessibilityControlService.instance
        if (service != null && service.globalAction("home")) return "navigated to home screen"
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            "navigated to home screen"
        } catch (e: Exception) {
            "could not navigate to home: ${e.message ?: \"unknown error\"}"
        }
    }

"""
        s = s.replace("    fun accessibilityAction(", go_home_code + "    fun accessibilityAction(", 1)

    old_acc_action = 'if (normalized == "call" || normalized == "callcontact" || normalized == "callbyname" || normalized == "directcall") return deviceActions.directCall(text); val service = AccessibilityControlService.instance ?: return "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu.";'
    new_acc_action = 'if (normalized == "call" || normalized == "callcontact" || normalized == "callbyname" || normalized == "directcall") return deviceActions.directCall(text); if (normalized == "home" || normalized == "gohome") return goHome(); val service = AccessibilityControlService.instance ?: return if (normalized == "home" || normalized == "gohome") goHome() else "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu.";'
    if old_acc_action in s:
        s = s.replace(old_acc_action, new_acc_action, 1)

    old_scroll_cases = '"scrollforward", "scrolldown" -> service.scroll(true); "scrollbackward", "scrollup" -> service.scroll(false);'
    new_scroll_cases = '"scroll", "scrollforward", "scrolldown", "down", "swipedown", "swipeup" -> service.scroll(true); "scrollbackward", "scrollup", "up" -> service.scroll(false);'
    if old_scroll_cases in s:
        s = s.replace(old_scroll_cases, new_scroll_cases, 1)

    old_known = 'val knownPackages = mapOf("youtube" to "com.google.android.youtube",'
    new_known = 'val cleanName = appName.lowercase().replace(Regex("^(open|launch|start|ଖୋଲ|khola|kholo)\\\\s+"), "").trim()\n        val wanted = normalize(if (cleanName.isNotBlank()) cleanName else appName)\n        val knownPackages = mapOf("youtube" to "com.google.android.youtube", "yt" to "com.google.android.youtube", "ୟୁଟ୍ୟୁବ୍" to "com.google.android.youtube", "ୟୁଟ୍ୟୁବ" to "com.google.android.youtube", "यूट्यूब" to "com.google.android.youtube", "whatsapp" to "com.whatsapp", "wa" to "com.whatsapp", "ହ୍ଵାଟ୍ସଆପ୍" to "com.whatsapp", "ହ୍ୱାଟ୍ସଆପ" to "com.whatsapp", "व्हाट्सएप" to "com.whatsapp", "chrome" to "com.android.chrome", "କ୍ରୋମ୍" to "com.android.chrome", "କ୍ରୋମ" to "com.android.chrome", "क्रोम" to "com.android.chrome", "camera" to "com.android.camera2", "କ୍ୟାମେରା" to "com.android.camera2", "settings" to "com.android.settings", "ସେଟିଙ୍ଗ୍" to "com.android.settings", "ସେଟିଂ" to "com.android.settings", "सेटिंग्स" to "com.android.settings", "phone" to "com.google.android.dialer", "messages" to "com.google.android.apps.messaging", "photos" to "com.google.android.apps.photos", "gallery" to "com.google.android.apps.photos",'
    if old_known in s and "cleanName" not in s:
        s = s.replace('val wanted = normalize(appName)\n        val knownPackages = mapOf("youtube" to "com.google.android.youtube",', new_known, 1)
        s = s.replace('val wanted = normalize(appName); val knownPackages = mapOf("youtube" to "com.google.android.youtube",', new_known, 1)

    old_launch_pkg = 'val launchIntent = pm.getLaunchIntentForPackage(packageName); if (launchIntent != null) return try { launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); app.startActivity(launchIntent); true } catch (_: Exception) { false }'
    new_launch_pkg = 'val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: pm.getLeanbackLaunchIntentForPackage(packageName)\n        if (launchIntent != null) return try { launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); app.startActivity(launchIntent); true } catch (_: Exception) { false }\n        return try { val fb = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER); setPackage(packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; app.startActivity(fb); true } catch (_: Exception) { false }'
    if old_launch_pkg in s:
        s = s.replace(old_launch_pkg, new_launch_pkg, 1)

    p_phone.write_text(s, encoding="utf-8")
    print("PhoneControlManager updated")

# 4. GeminiLiveClient.kt: fix sendText to use clientContent
p_live = PKG / "GeminiLiveClient.kt"
if p_live.exists():
    s = p_live.read_text(encoding="utf-8")
    old_send_text = 'enqueueOrSend(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString())'
    new_send_text = 'val msg = JSONObject().put("clientContent", JSONObject().apply { put("turns", JSONArray().put(JSONObject().apply { put("role", "user"); put("parts", JSONArray().put(JSONObject().put("text", text))) })); put("turnComplete", true) }); enqueueOrSend(msg.toString())'
    if old_send_text in s:
        s = s.replace(old_send_text, new_send_text, 1)
        p_live.write_text(s, encoding="utf-8")
        print("GeminiLiveClient sendText updated")

# 5. Models: persistent conversation metadata
p_models = PKG / "Models.kt"
s = p_models.read_text(encoding="utf-8")
if "data class AnuConversationSummary" not in s:
    s = replace_once(s, "enum class ChatRole { USER, ANU, SYSTEM }\n", """enum class ChatRole { USER, ANU, SYSTEM }

data class AnuConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: Long
)
""", "conversation model")
if "val chatConversations: List<AnuConversationSummary>" not in s:
    s = replace_once(s, "    val chatMessages: List<ChatMessage> = emptyList(),\n", """    val chatMessages: List<ChatMessage> = emptyList(),
    val chatConversations: List<AnuConversationSummary> = emptyList(),
    val activeConversationId: String = "",
""", "conversation state")
p_models.write_text(s, encoding="utf-8")
print("Models.kt updated")

# 6. Session manager: conversation tracking & device control routing
p_sm = PKG / "ZoyaSessionManager.kt"
s = p_sm.read_text(encoding="utf-8")
if "CONVERSATION_MARKER" not in s:
    s = replace_once(s, "    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n", """    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val CONVERSATION_MARKER = "__ANU_CONVERSATION__"
    private const val ACTIVE_CONVERSATION_PREF = "active_conversation_id"
""", "conversation constants")

if "private fun splitConversations(" not in s:
    helper = '''    private fun conversationMarker(id: String) = ChatMessage(UUID.randomUUID().toString(), ChatRole.SYSTEM, "$CONVERSATION_MARKER|$id", System.currentTimeMillis())

    private fun parseConversationId(message: ChatMessage): String? {
        if (message.role != ChatRole.SYSTEM || !message.text.startsWith("$CONVERSATION_MARKER|")) return null
        return message.text.removePrefix("$CONVERSATION_MARKER|").trim().ifBlank { null }
    }

    private fun splitConversations(messages: List<ChatMessage>): LinkedHashMap<String, MutableList<ChatMessage>> {
        val result = linkedMapOf<String, MutableList<ChatMessage>>()
        var current = "default"
        messages.sortedBy { it.timestampMillis }.forEach { message ->
            val marker = parseConversationId(message)
            if (marker != null) {
                current = marker
                result.getOrPut(current) { mutableListOf() }
            } else if (message.role != ChatRole.SYSTEM) {
                result.getOrPut(current) { mutableListOf() }.add(message)
            }
        }
        return result
    }

    private fun conversationSummaries(messages: List<ChatMessage>): List<AnuConversationSummary> =
        splitConversations(messages).map { (id, items) ->
            val first = items.firstOrNull { it.role == ChatRole.USER }
            val title = first?.text?.replace("\\n", " ")?.trim()?.take(42)?.ifBlank { "Conversation" } ?: "Conversation"
            AnuConversationSummary(id, title, items.size, items.maxOfOrNull { it.timestampMillis } ?: 0L)
        }.filter { it.messageCount > 0 }.sortedByDescending { it.updatedAt }

    private fun messagesForConversation(messages: List<ChatMessage>, id: String): List<ChatMessage> =
        splitConversations(messages)[id].orEmpty()

    fun newConversation() {
        ensureInitialized()
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, id).apply()
        scope.launch { repository.saveChatMessage(conversationMarker(id)) }
        _state.update { it.copy(chatMessages = emptyList(), activeConversationId = id, isAnuResponding = false, error = null) }
    }

    fun selectConversation(id: String) {
        ensureInitialized()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, id).apply()
        scope.launch {
            val all = repository.getAllChatMessages()
            _state.update { it.copy(chatMessages = messagesForConversation(all, id), activeConversationId = id, isAnuResponding = false) }
        }
    }

    private fun tryDirectDeviceAction(text: String): String? {
        val lower = text.trim().lowercase()
        if (lower in listOf("home", "go home", "go to home", "home screen", "open home", "ଘରକୁ ଯାଅ", "home ku ja", "home jao", "घर जाओ", "होम")) {
            return phoneControls.goHome()
        }
        if (lower.contains("scroll down") || lower.contains("ତଳକୁ scroll") || lower == "scroll" || lower.contains("scroll karo") || lower.contains("नीचे स्क्रॉल")) {
            return phoneControls.accessibilityAction("scrolldown")
        }
        if (lower.contains("scroll up") || lower.contains("ଉପରକୁ scroll") || lower.contains("ऊपर स्क्रॉल")) {
            return phoneControls.accessibilityAction("scrollup")
        }
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("ଟର୍ଚ୍ଚ ଜଳାଅ") || lower.contains("ଟର୍ଚ ଅନ") || lower.contains("टॉर्च ऑन")) {
            return phoneControls.flashlight(true)
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("ଟର୍ଚ୍ଚ ବନ୍ଦ କର") || lower.contains("ଟର୍ଚ ଅଫ") || lower.contains("टॉर्च बंद")) {
            return phoneControls.flashlight(false)
        }
        if (lower.contains("volume up") || lower.contains("volume badhao") || lower.contains("ଭଲ୍ୟୁମ ବଢ଼ାଅ") || lower.contains("sound up") || lower.contains("आवाज बढ़ाओ")) {
            return phoneControls.volumeUp()
        }
        if (lower.contains("volume down") || lower.contains("volume kamao") || lower.contains("ଭଲ୍ୟୁମ କମାଅ") || lower.contains("sound down") || lower.contains("आवाज कम करो")) {
            return phoneControls.volumeDown()
        }
        val appPrefixes = listOf("open ", "launch ", "start ", "ଖୋଲ ", "khola ", "kholo ")
        for (prefix in appPrefixes) {
            if (lower.startsWith(prefix)) {
                val app = lower.removePrefix(prefix).trim()
                if (app.isNotBlank()) return phoneControls.openApp(app)
            }
        }
        if (lower.endsWith(" ଖୋଲ") || lower.endsWith(" kholo") || lower.endsWith(" khola")) {
            val app = lower.substringBeforeLast(" ").trim()
            if (app.isNotBlank()) return phoneControls.openApp(app)
        }
        return null
    }

'''
    s = replace_once(s, "    fun setLanguage(lang: ZoyaLanguage) {", helper + "    fun setLanguage(lang: ZoyaLanguage) {", "session helpers")

# Enhance executeTool with aliases for home, scroll, modifySystem, openApp
if '"modifySystem"' not in s:
    old_tool_cases = '''        "openApp" -> phoneControls.openApp(args.optString("appName"))
        "phoneAction" -> when (args.optString("action").trim().lowercase()) {'''
    new_tool_cases = '''        "openApp", "launchApp", "open_app" -> phoneControls.openApp(args.optString("appName").ifBlank { args.optString("name").ifBlank { args.optString("app") } })
        "goHome", "home", "go_home" -> phoneControls.goHome()
        "scroll", "scrollScreen" -> phoneControls.accessibilityAction(if (args.optBoolean("backward", false) || args.optString("direction").contains("up")) "scrollup" else "scrolldown")
        "phoneAction", "modifySystem", "systemAction", "controlPhone" -> when (args.optString("action").ifBlank { args.optString("command") }.trim().lowercase()) {
            "home", "gohome", "go_home" -> phoneControls.goHome()
            "scroll", "scrolldown", "scroll_down" -> phoneControls.accessibilityAction("scrolldown")
            "scrollup", "scroll_up" -> phoneControls.accessibilityAction("scrollup")
            "open_app", "launch_app" -> phoneControls.openApp(args.optString("appName").ifBlank { args.optString("text") })
            "notifications", "open_notifications" -> phoneControls.accessibilityAction("notifications")
            "quicksettings", "quick_settings" -> phoneControls.accessibilityAction("quicksettings")
            "display_settings", "display" -> phoneControls.openDisplaySettings()
            "sound_settings", "sound" -> phoneControls.openSoundSettings()
            "mute" -> phoneControls.setMediaVolume(0)
            "set_volume" -> phoneControls.setMediaVolume(args.optInt("percent", 50))
            "set_brightness" -> phoneControls.changeBrightness(args.optInt("percent", 15))'''
    if old_tool_cases in s:
        s = s.replace(old_tool_cases, new_tool_cases, 1)

# Direct command execution in sendText
if "tryDirectDeviceAction" in s and "val directResult = tryDirectDeviceAction(clean)" not in s:
    old_send = 'val proactive = clean.startsWith("[PROACTIVE SYSTEM EVENT]")'
    new_send = '''val directResult = tryDirectDeviceAction(clean)
        if (directResult != null) {
            val userMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
            val anuMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, directResult, System.currentTimeMillis() + 10L)
            _state.update { it.copy(chatMessages = it.chatMessages + userMsg + anuMsg, isAnuResponding = false) }
            scope.launch { repository.saveChatMessage(userMsg); repository.saveChatMessage(anuMsg) }
            if (isConnected()) client?.sendText("[DIRECT ACTION EXECUTED] Result: $directResult. Please acknowledge concisely in ${_state.value.language.label}.")
            return
        }
        val proactive = clean.startsWith("[PROACTIVE SYSTEM EVENT]")'''
    if old_send in s:
        s = s.replace(old_send, new_send, 1)

# Session initialization with conversation history
old_init = '''        _state.value = ZoyaUiState(language = language, quote = idleQuotes[language]?.random().orEmpty(), tasks = loadTasks())
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages -> _state.update { it.copy(chatMessages = messages) } } } }'''
new_init = '''        val persisted = runBlocking(Dispatchers.IO) { repository.getAllChatMessages() }
        val activeId = prefs.getString(ACTIVE_CONVERSATION_PREF, null) ?: UUID.randomUUID().toString()
        prefs.edit().putString(ACTIVE_CONVERSATION_PREF, activeId).apply()
        _state.value = ZoyaUiState(
            language = language,
            quote = idleQuotes[language]?.random().orEmpty(),
            tasks = loadTasks(),
            chatMessages = messagesForConversation(persisted, activeId),
            chatConversations = conversationSummaries(persisted),
            activeConversationId = activeId
        )
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages ->
            val id = prefs.getString(ACTIVE_CONVERSATION_PREF, activeId) ?: activeId
            _state.update { it.copy(chatMessages = messagesForConversation(messages, id), chatConversations = conversationSummaries(messages), activeConversationId = id) }
        } } }'''
if old_init in s:
    s = s.replace(old_init, new_init, 1)

p_sm.write_text(s, encoding="utf-8")
print("ZoyaSessionManager updated")

# 7. ViewModel facade
p_vm = PKG / "ZoyaViewModel.kt"
s = p_vm.read_text(encoding="utf-8")
if "fun newConversation()" not in s:
    s = replace_once(s, "    fun clearMemories() = ZoyaSessionManager.clearMemories()\n", """    fun newConversation() = ZoyaSessionManager.newConversation()
    fun selectConversation(id: String) = ZoyaSessionManager.selectConversation(id)
    fun clearMemories() = ZoyaSessionManager.clearMemories()
""", "viewmodel conversation facade")
p_vm.write_text(s, encoding="utf-8")
print("ZoyaViewModel updated")

# 8. MainActivity.kt: in-screen Conversation History section & dialog
p_main = PKG / "MainActivity.kt"
s = p_main.read_text(encoding="utf-8")
if "onNewConversation = { viewModel.newConversation() }" not in s:
    s = replace_once(
        s,
        """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onVoiceClick = {""",
        """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onNewConversation = { viewModel.newConversation() },
                            onSelectConversation = { id -> viewModel.selectConversation(id) },
                            onVoiceClick = {""",
        "chat screen callbacks"
    )

s = replace_once(s, """fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""", """fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""", "chat screen signature") if "onNewConversation: () -> Unit" not in s else s

# Top header bar: Add Conversations History button and New button
if 'Text("New", color = AnuPrimary' not in s:
    s = replace_once(s, """            Box {
                IconButton(onClick = { showMenu = true }) {""", """            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNewConversation) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = AnuPrimary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("New", color = AnuPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                IconButton(onClick = { showMenu = true }) {""", "new conversation button")
    s = replace_once(s, """            }
        }

        // Chat Message List or Empty Placeholder""", """                }
            }
        }

        // Chat Message List or Empty Placeholder""", "new button container closure")

# Dialog signature
s = replace_once(s, """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    conversations: List<AnuConversationSummary>,
    activeConversationId: String,
    onSelectConversation: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", "history dialog signature") if "conversations: List<AnuConversationSummary>" not in s else s

if "conversations = state.chatConversations" not in s:
    s = replace_once(s, """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            onDismiss = { showChatHistoryDialog = false },""", """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            conversations = state.chatConversations,
            activeConversationId = state.activeConversationId,
            onSelectConversation = { id -> onSelectConversation(id) },
            onDismiss = { showChatHistoryDialog = false },""", "history dialog call")

if "val filteredConversations =" not in s:
    s = replace_once(s, """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
""", """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
""", "conversation filtering")

if "Saved conversations" not in s:
    block = """                    Text("Saved conversations", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(6.dp))
                    if (filteredConversations.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredConversations, key = { it.id }) { conversation ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (conversation.id == activeConversationId) AnuLavenderBg else AnuBackground,
                                    border = BorderStroke(1.dp, if (conversation.id == activeConversationId) AnuPrimary.copy(alpha = 0.45f) else AnuBorder),
                                    modifier = Modifier.fillMaxWidth().clickable { onSelectConversation(conversation.id) }
                                ) {
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(conversation.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AnuTextDark, maxLines = 1)
                                        Text("${conversation.messageCount} messages", fontSize = 10.sp, color = AnuTextMuted)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No saved conversations yet.", fontSize = 11.5.sp, color = AnuTextMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Messages in current conversation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(4.dp))

"""
    s = replace_once(s, """                    Spacer(Modifier.height(10.dp))

                    if (filteredMessages.isEmpty()) {""", """                    Spacer(Modifier.height(10.dp))

""" + block + """                    if (filteredMessages.isEmpty()) {""", "conversation list UI")

# IN-SCREEN Conversation History Section in AnuChatScreen
if "Conversation History Section" not in s:
    in_screen_conversations = """        // Conversation History Section: displays all conversations conversation-wise
        if (state.chatConversations.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = AnuBackground,
                border = BorderStroke(1.dp, AnuBorder)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💬 Chat History (${state.chatConversations.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AnuPrimary
                        )
                        Text(
                            "Tap to switch",
                            fontSize = 10.sp,
                            color = AnuTextMuted
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.chatConversations, key = { it.id }) { conv ->
                            val active = conv.id == state.activeConversationId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) AnuLavenderBg else Color.Transparent,
                                border = BorderStroke(1.dp, if (active) AnuPrimary else AnuBorder),
                                modifier = Modifier.clickable { onSelectConversation(conv.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        conv.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = if (active) AnuPrimary else AnuTextDark,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "(${conv.messageCount})",
                                        fontSize = 9.5.sp,
                                        color = AnuTextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
"""
    s = replace_once(
        s,
        "        // Chat Message List or Empty Placeholder",
        in_screen_conversations + "\n        // Chat Message List or Empty Placeholder",
        "in-screen conversation history section"
    )

p_main.write_text(s, encoding="utf-8")
print("MainActivity updated with in-screen conversation history section")

print("All patches applied safely and verified.")
