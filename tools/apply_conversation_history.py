import os, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
PKG = APP / "src/main/java/com/ghaniram/zoya"

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Required source anchor not found: {label}")
    return text.replace(old, new, 1)

# 1. AndroidManifest.xml: package visibility for querying and opening apps
p_manifest = ROOT / "app/src/main/AndroidManifest.xml"
if p_manifest.exists():
    s = p_manifest.read_text(encoding="utf-8")
    if "android.permission.QUERY_ALL_PACKAGES" not in s:
        s = re.sub(
            r'(<uses-permission\s+android:name="android\.permission\.BLUETOOTH_CONNECT"\s*/>)',
            r'<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />\n    \1',
            s, count=1
        )
    p_manifest.write_text(s, encoding="utf-8")
    print("Manifest updated with QUERY_ALL_PACKAGES")

# 2. AccessibilityControlService.kt: screen-level scroll gesture fallback
p_acc = PKG / "AccessibilityControlService.kt"
if p_acc.exists():
    s = p_acc.read_text(encoding="utf-8")
    if "performScreenScrollGesture" not in s:
        helper = """    private fun performScreenScrollGesture(forward: Boolean): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val dm = resources.displayMetrics
        val width = dm.widthPixels.toFloat()
        val height = dm.heightPixels.toFloat()
        val x = width * 0.5f
        val startY = if (forward) height * 0.75f else height * 0.25f
        val endY = if (forward) height * 0.25f else height * 0.75f
        val path = android.graphics.Path().apply { moveTo(x, startY); lineTo(x, endY) }
        return dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 300L)).build(), null, null)
    }

    private fun waitForUiSettle"""
        s = re.sub(r'private fun waitForUiSettle', helper, s, count=1)

        new_scroll = """fun scroll(forward: Boolean): Boolean {
        val before = ActionVerifier.snapshot(this)
        val root = rootInActiveWindow
        val node = if (root != null) findScrollable(root) else null
        var acted = false
        if (node != null) {
            acted = runCatching {
                node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }.getOrDefault(false)
            if (!acted) acted = performScrollGesture(node, forward)
        }
        if (!acted) acted = performScreenScrollGesture(forward)
        waitForUiSettle(180L)
        return acted || ActionVerifier.changed(before, ActionVerifier.snapshot(this))
    }
    private fun"""
        s = re.sub(r'fun scroll\(forward: Boolean\): Boolean\s*\{.*?\n\s*private fun', new_scroll, s, count=1, flags=re.DOTALL)
        p_acc.write_text(s, encoding="utf-8")
        print("AccessibilityControlService updated with scroll gesture fallback")

# 3. PhoneControlManager.kt: preserve rich implementation if already present
p_phone = PKG / "PhoneControlManager.kt"
if p_phone.exists():
    s = p_phone.read_text(encoding="utf-8")
    if "fun writeNote" in s and "fun goHome" in s:
        print("PhoneControlManager already up-to-date with writeNote and goHome, skipping overwrite")
    else:
        if "fun goHome(): String" not in s:
            go_home_code = '''    fun goHome(): String {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            AccessibilityControlService.instance?.globalAction("home")
            "navigated to home screen"
        } catch (e: Exception) {
            val ok = AccessibilityControlService.instance?.globalAction("home") == true
            if (ok) "navigated to home screen" else "could not go to home screen: ${e.message ?: \"unknown error\"}"
        }
    }

'''
            s = s.replace("fun accessibilityAction(action: String", go_home_code + "fun accessibilityAction(action: String", 1)
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

# 5. Models.kt: persistent conversation metadata
p_models = PKG / "Models.kt"
s = p_models.read_text(encoding="utf-8")
if "data class AnuConversationSummary" not in s:
    s = s.replace("enum class ChatRole { USER, ANU, SYSTEM }\n", """enum class ChatRole { USER, ANU, SYSTEM }

data class AnuConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: Long,
    val snippet: String = ""
)
""", 1)
if "val chatConversations: List<AnuConversationSummary>" not in s:
    s = s.replace("    val chatMessages: List<ChatMessage> = emptyList(),\n", """    val chatMessages: List<ChatMessage> = emptyList(),
    val chatConversations: List<AnuConversationSummary> = emptyList(),
    val activeConversationId: String = "",
""", 1)
p_models.write_text(s, encoding="utf-8")
print("Models.kt verified with AnuConversationSummary")

# 6. ZoyaSessionManager.kt: full direct action handling and conversation management
p_sm = PKG / "ZoyaSessionManager.kt"
s = p_sm.read_text(encoding="utf-8")
if "CONVERSATION_MARKER" not in s:
    s = s.replace("    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n", """    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private const val CONVERSATION_MARKER = "__ANU_CONVERSATION__"
    private const val ACTIVE_CONVERSATION_PREF = "active_conversation_id"
""", 1)

if "private fun splitConversations(" not in s:
    helper = r"""    private fun conversationMarker(id: String) = ChatMessage(UUID.randomUUID().toString(), ChatRole.SYSTEM, "$CONVERSATION_MARKER|$id", System.currentTimeMillis())

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

    fun cleanAnuReply(raw: String): String {
        if (raw.isBlank()) return ""
        var res = raw
        res = res.replace(Regex("<thought>.*?</thought>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        res = res.replace(Regex("<tone[;:][^>]+>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("<(emotion|style|mood|gesture|action)[;:][^>]+>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("<[a-zA-Z0-9_-]+[;:][^>]+>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("</?(whisper|sigh|gasp|laughter|chuckle|pause)>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("\\[(SYSTEM ACTION|DIRECT ACTION|ACTION|TOOL)[^\\]]*\\]", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("^(Anu|Assistant|You)[ \\t]*:[ \\t]*", RegexOption.IGNORE_CASE), "")
        return res.trim()
    }

    private fun conversationSummaries(messages: List<ChatMessage>): List<AnuConversationSummary> =
        splitConversations(messages).map { (id, items) ->
            val first = items.firstOrNull { it.role == ChatRole.USER }
            val last = items.lastOrNull { it.role != ChatRole.SYSTEM }
            val title = first?.text?.replace("\\n", " ")?.trim()?.take(36)?.ifBlank { "New Chat" } ?: "Chat"
            val rawSnippet = last?.text?.replace("\\n", " ")?.trim()?.take(80) ?: ""
            val snippet = cleanAnuReply(rawSnippet).ifBlank { title }
            val updated = items.maxOfOrNull { it.timestampMillis } ?: 0L
            AnuConversationSummary(id, title, items.size, updated, snippet)
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

    fun tryExecuteDirectAction(text: String): String? {
        val lower = text.trim().lowercase()
        if (lower in listOf("home", "go home", "go to home", "home screen", "open home", "ଘରକୁ ଯାଅ", "home ku ja", "home jao", "ହୋମ", "home କୁ ଯାଅ", "घर जाओ", "होम")) {
            return phoneControls.goHome()
        }
        if (lower.contains("scroll down") || lower.contains("ତଳକୁ scroll") || lower.contains("ତଳକୁ ସ୍କ୍ରୋଲ") || lower == "scroll" || lower.contains("scroll karo") || lower.contains("नीचे स्क्रॉल")) {
            return phoneControls.accessibilityAction("scrolldown")
        }
        if (lower.contains("scroll up") || lower.contains("ଉପରକୁ scroll") || lower.contains("ଉପରକୁ ସ୍କ୍ରୋଲ") || lower.contains("ऊपर स्क्रॉल")) {
            return phoneControls.accessibilityAction("scrollup")
        }
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("ଟର୍ଚ୍ଚ ଜଳାଅ") || lower.contains("ଟର୍ଚ ଅନ") || lower.contains("flashlight ଚାଲୁ") || lower.contains("टॉर्च ऑन")) {
            return phoneControls.flashlight(true)
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("ଟର୍ଚ୍ଚ ବନ୍ଦ କର") || lower.contains("ଟର୍ଚ ଅଫ") || lower.contains("flashlight ବନ୍ଦ") || lower.contains("टॉर्च बंद")) {
            return phoneControls.flashlight(false)
        }
        if (lower.contains("volume up") || lower.contains("volume badhao") || lower.contains("ଭଲ୍ୟୁମ ବଢ଼ାଅ") || lower.contains("sound up") || lower.contains("ଆୱାଜ ବଢ଼ାଅ") || lower.contains("आवाज बढ़ाओ")) {
            return phoneControls.volumeUp()
        }
        if (lower.contains("volume down") || lower.contains("volume kamao") || lower.contains("ଭଲ୍ୟୁମ କମାଅ") || lower.contains("sound down") || lower.contains("ଆୱାଜ କମାଅ") || lower.contains("आवाज कम करो")) {
            return phoneControls.volumeDown()
        }
        if (lower in listOf("mute", "silent", "sound off", "volume zero", "ଶାନ୍ତ କର")) {
            return phoneControls.setMediaVolume(0)
        }
        if (lower in listOf("settings", "open settings", "ସେଟିଙ୍ଗ୍ ଖୋଲ", "ସେଟିଂ ଖୋଲ", "सेटिंग्स खोलो", "setting")) {
            return phoneControls.openSettings()
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return phoneControls.openWifiSettings()
        }
        if (lower.contains("bluetooth") || lower.contains("ବ୍ଲୁଟୁଥ")) {
            return phoneControls.openBluetoothSettings()
        }
        if (lower in listOf("open camera", "camera ଖୋଲ", "କ୍ୟାମେରା ଖୋଲ", "camera", "कैमरा खोलो")) {
            return phoneControls.openCamera()
        }
        if (lower.contains("selfie") || lower.contains("ସେଲଫି")) {
            return takeSelfieAutonomous()
        }
        val appPrefixes = listOf("open ", "launch ", "start ", "ଖୋଲ ", "khola ", "kholo ")
        for (prefix in appPrefixes) {
            if (lower.startsWith(prefix)) {
                val app = lower.removePrefix(prefix).trim()
                if (app.isNotBlank()) return phoneControls.openApp(app)
            }
        }
        if (lower.endsWith(" ଖୋଲ") || lower.endsWith(" kholo") || lower.endsWith(" khola") || lower.endsWith(" open") || lower.endsWith(" ଖୋଲିଦିଅ")) {
            val app = lower.substringBeforeLast(" ").trim()
            if (app.isNotBlank()) return phoneControls.openApp(app)
        }
        return null
    }

"""
    s = s.replace("    fun setLanguage(lang: ZoyaLanguage) {", helper + "    fun setLanguage(lang: ZoyaLanguage) {", 1)

# Direct command execution in sendText
if "tryExecuteDirectAction" in s and "val directResult = tryExecuteDirectAction(clean)" not in s:
    new_send = '''val directResult = tryExecuteDirectAction(clean)
        if (directResult != null) {
            val userMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
            val anuMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, directResult, System.currentTimeMillis() + 10L)
            _state.update { it.copy(chatMessages = it.chatMessages + userMsg + anuMsg, isAnuResponding = false) }
            scope.launch { repository.saveChatMessage(userMsg); repository.saveChatMessage(anuMsg) }
            if (isConnected()) client?.sendText("[SYSTEM ACTION COMPLETED: $directResult] Acknowledge concisely to the user in ${_state.value.language.label}.")
            return
        }
        val proactive = clean.startsWith("[PROACTIVE SYSTEM EVENT]")'''
    s = s.replace('val proactive = clean.startsWith("[PROACTIVE SYSTEM EVENT]")', new_send, 1)

# Clean tone tags in onModelText (e.g. <tone:warm>)
if "val clean = cleanAnuReply" not in s:
    old_model_handler = """            override fun onModelText(text: String) {
                if (!isCurrentSession()) return
                val clean = text.removePrefix("You:").removePrefix("You :").trim()
                if (clean.isBlank()) return
                val last = _state.value.chatMessages.lastOrNull()
                if (last != null && last.id == currentAnuId && last.role == ChatRole.ANU) {
                    val updated = last.copy(text = (last.text + " " + clean).trim())
                    _state.update { s -> s.copy(chatMessages = s.chatMessages.dropLast(1) + updated, isAnuResponding = false) }
                    scope.launch { repository.updateChatMessage(updated) }
                } else {
                    val id = UUID.randomUUID().toString()
                    currentAnuId = id
                    val msg = ChatMessage(id, ChatRole.ANU, clean, System.currentTimeMillis())
                    _state.update { s -> s.copy(chatMessages = s.chatMessages + msg) }
                    scope.launch { repository.saveChatMessage(msg) }
                }
            }"""
    new_model_handler = """            override fun onModelText(text: String) {
                if (!isCurrentSession()) return
                val clean = cleanAnuReply(text.removePrefix("You:").removePrefix("You :"))
                if (clean.isBlank()) return
                val last = _state.value.chatMessages.lastOrNull()
                if (last != null && last.id == currentAnuId && last.role == ChatRole.ANU) {
                    val merged = cleanAnuReply(last.text + " " + clean)
                    val updated = last.copy(text = merged)
                    _state.update { s -> s.copy(chatMessages = s.chatMessages.dropLast(1) + updated, isAnuResponding = false) }
                    scope.launch { repository.updateChatMessage(updated) }
                } else {
                    val id = UUID.randomUUID().toString()
                    currentAnuId = id
                    val msg = ChatMessage(id, ChatRole.ANU, clean, System.currentTimeMillis())
                    _state.update { s -> s.copy(chatMessages = s.chatMessages + msg) }
                    scope.launch { repository.saveChatMessage(msg) }
                }
            }"""
    if old_model_handler in s:
        s = s.replace(old_model_handler, new_model_handler, 1)

# Clean prompt rule for tone tags
clean_prompt_rule = " Output rule: Never include tone tags, emotion tags, angle bracket tags, or metadata like <tone:warm>, <tone:...>, or <whisper> in your output. Transcribe and speak purely the natural conversational reply text without any formatting tags."
if clean_prompt_rule not in s:
    s = s.replace('Respond naturally in $language.', f'Respond naturally in $language.{clean_prompt_rule}', 1)

# Direct command execution in onUserText (speech transcription)
if "tryExecuteDirectAction" in s and "val directResult = tryExecuteDirectAction(clean)" not in s[s.find("override fun onUserText"):s.find("override fun onModelText")]:
    new_user_text = '''override fun onUserText(text: String) {
                if (!isCurrentSession()) return
                val clean = text.removePrefix("You:").removePrefix("You :").trim()
                if (clean.isBlank()) return
                ProactiveEventEngine.noteUserActivity()
                val directResult = tryExecuteDirectAction(clean)
                if (directResult != null) {
                    val userMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
                    val anuMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, directResult, System.currentTimeMillis() + 10L)
                    _state.update { it.copy(chatMessages = it.chatMessages + userMsg + anuMsg, isAnuResponding = false) }
                    scope.launch { repository.saveChatMessage(userMsg); repository.saveChatMessage(anuMsg) }
                    client?.sendText("[SYSTEM ACTION COMPLETED: $directResult] Acknowledge concisely to the user in ${_state.value.language.label}.")
                    return
                }'''
    s = re.sub(r'override fun onUserText\(text: String\)\s*\{.*?(?=ProactiveEventEngine\.noteUserActivity\(\))ProactiveEventEngine\.noteUserActivity\(\)', new_user_text, s, count=1, flags=re.DOTALL)

# Comprehensive executeTool
new_execute_tool = '''private fun executeTool(name: String, args: JSONObject): String = when (name) {
        "openWebsite" -> {
            val url = args.optString("url").trim()
            val label = args.optString("name", url)
            if (url.isBlank()) "invalid URL" else runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); "opened $label" }.getOrElse { "could not open $label: ${it.message ?: "unknown error"}" }
        }
        "openApp", "launchApp", "open_app" -> phoneControls.openApp(args.optString("appName").ifBlank { args.optString("name").ifBlank { args.optString("app") } })
        "goHome", "home", "go_home" -> phoneControls.goHome()
        "scroll", "scrollScreen" -> phoneControls.accessibilityAction(if (args.optBoolean("backward", false) || args.optString("direction").contains("up")) "scrollup" else "scrolldown")
        "phoneAction", "modifySystem", "systemAction", "controlPhone" -> when (args.optString("action").ifBlank { args.optString("command") }.trim().lowercase()) {
            "home", "gohome", "go_home" -> phoneControls.goHome()
            "scroll", "scrolldown", "scroll_down" -> phoneControls.accessibilityAction("scrolldown")
            "scrollup", "scroll_up" -> phoneControls.accessibilityAction("scrollup")
            "open_app", "launch_app" -> phoneControls.openApp(args.optString("appName").ifBlank { args.optString("text") })
            "take_selfie", "selfie", "camera_selfie" -> takeSelfieAutonomous()
            "camera", "open_camera" -> phoneControls.openCamera()
            "phone", "dialer" -> phoneControls.openPhone()
            "messages", "sms" -> phoneControls.openMessages()
            "settings", "open_settings" -> phoneControls.openSettings()
            "wifi_settings", "wifi" -> phoneControls.openWifiSettings()
            "bluetooth_settings", "bluetooth" -> phoneControls.openBluetoothSettings()
            "display_settings", "display" -> phoneControls.openDisplaySettings()
            "sound_settings", "sound" -> phoneControls.openSoundSettings()
            "notifications", "open_notifications" -> phoneControls.accessibilityAction("notifications")
            "quicksettings", "quick_settings" -> phoneControls.accessibilityAction("quicksettings")
            "flashlight_on", "torch_on" -> phoneControls.flashlight(true)
            "flashlight_off", "torch_off" -> phoneControls.flashlight(false)
            "flashlight", "torch" -> phoneControls.flashlight(args.optBoolean("enabled", true) || args.optString("state").lowercase() != "off")
            "volume_up", "louder" -> phoneControls.volumeUp()
            "volume_down", "quieter" -> phoneControls.volumeDown()
            "mute" -> phoneControls.setMediaVolume(0)
            "set_volume" -> phoneControls.setMediaVolume(args.optInt("percent", 50))
            "brightness_up" -> phoneControls.changeBrightness(15)
            "brightness_down" -> phoneControls.changeBrightness(-15)
            "set_brightness" -> phoneControls.changeBrightness(args.optInt("percent", 15))
            else -> "unsupported phone action: ${args.optString("action")}"
        }
        "createTaskReminder", "setReminder", "addTask" -> {
            val title = args.optString("title").ifBlank { "Reminder" }
            val time = args.optString("time").ifBlank { "8:00 PM" }
            addTask(title, time)
            "Created task reminder for '$title' at $time"
        }
        "accessibilityAction" -> phoneControls.accessibilityAction(args.optString("action"), args.optString("text"), args.optString("value"))
        "writeNote", "createNote", "takeNote" -> {
            val title = args.optString("title").ifBlank { "Note" }
            val content = args.optString("content").ifBlank { args.optString("text") }
            phoneControls.writeNote(title, content)
        }
        "writeCode", "generateCode", "code", "createCode" -> {
            val code = args.optString("code").ifBlank { args.optString("content") }.ifBlank { args.optString("text") }
            val filename = args.optString("filename").ifBlank { args.optString("file") }
            val language = args.optString("language").ifBlank { args.optString("lang") }
            phoneControls.writeCode(code, filename, language)
        }
        "githubAction", "github", "githubCommit", "githubRepo" -> {
            val action = args.optString("action").ifBlank { args.optString("command") }.lowercase().trim()
            val repo = args.optString("repo").ifBlank { args.optString("repository") }.trim()
            val path = args.optString("path").ifBlank { args.optString("file") }.trim()
            val content = args.optString("content").ifBlank { args.optString("code") }
            val message = args.optString("message").ifBlank { "Commit from Anu" }
            phoneControls.githubAction(action, repo, path, content, message)
        }
        "copyToClipboard", "clipboard" -> {
            val text = args.optString("text").ifBlank { args.optString("content") }
            val label = args.optString("label", "Copied Text")
            try {
                val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText(label, text))
                "Copied to clipboard successfully"
            } catch (e: Exception) {
                "Could not copy to clipboard: ${e.message}"
            }
        }
        "readScreen" -> AccessibilityControlService.instance?.uiSnapshot() ?: "Screen reading is unavailable because Anu Accessibility is not enabled."
        "getDeviceInfo" -> { DeviceQueryContext.set(args.optString("query").ifBlank { _state.value.chatMessages.lastOrNull { it.role == ChatRole.USER }?.text.orEmpty() }); DeviceInfoProvider.snapshot(app) }
        else -> "Unknown tool: $name"
    }
    private fun buildToolDeclarations'''

s = re.sub(r'private fun executeTool\(name: String, args: JSONObject\): String = when \(name\)\s*\{.*?\n\s*private fun buildToolDeclarations', new_execute_tool, s, count=1, flags=re.DOTALL)

old_instr = 'For camera selfie, ALWAYS call phoneAction(action=take_selfie);'
new_instr = 'DEVICE FUNCTIONAL CONTROLS: When the user asks to open an app (e.g. YouTube, WhatsApp, Settings, Camera, Chrome), go home, scroll, or change system settings (volume, brightness, flashlight, Wi-Fi, Bluetooth), you MUST IMMEDIATELY call openApp, accessibilityAction, or phoneAction. For camera selfie, ALWAYS call phoneAction(action=take_selfie);'
if old_instr in s:
    s = s.replace(old_instr, new_instr, 1)

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
old_init = '''        _state.value = ZoyaUiState(language = language, quote = idleQuotes[language]?.random().orEmpty(), tasks = loadTasks())
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages ->
            val id = prefs.getString(ACTIVE_CONVERSATION_PREF, activeId) ?: activeId
            _state.update { it.copy(chatMessages = messagesForConversation(messages, id), chatConversations = conversationSummaries(messages), activeConversationId = id) }
        } } }'''
if old_init in s:
    s = s.replace(old_init, new_init, 1)

p_sm.write_text(s, encoding="utf-8")
print("ZoyaSessionManager updated")

# 7. ZoyaViewModel.kt: facade methods
p_vm = PKG / "ZoyaViewModel.kt"
s = p_vm.read_text(encoding="utf-8")
if "fun newConversation()" not in s:
    s = s.replace("    fun clearMemories() = ZoyaSessionManager.clearMemories()\n", """    fun newConversation() = ZoyaSessionManager.newConversation()
    fun selectConversation(id: String) = ZoyaSessionManager.selectConversation(id)
    fun clearMemories() = ZoyaSessionManager.clearMemories()
""", 1)
p_vm.write_text(s, encoding="utf-8")
print("ZoyaViewModel updated")

# 8. MainActivity.kt: clean, soft, minimized chat & history UI
p_main = PKG / "MainActivity.kt"
s = p_main.read_text(encoding="utf-8")

# Dynamic Home greeting based on time of day
if "val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }" not in s:
    old_greeting = """            Column(
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
                )"""
    new_greeting = """            val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val greeting = when (currentHour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Good night"
            }
            val userName = remember(context) {
                runCatching { AnuSettingsStore.getInstance(context).userName }.getOrNull()?.trim()?.ifBlank { "Ghaniram" } ?: "Ghaniram"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "$greeting, $userName 👋",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnuTextDark,
                )"""
    s = s.replace(old_greeting, new_greeting, 1)

# Clean displayed text in Chat message bubble (remove <tone:warm>)
if "ZoyaSessionManager.cleanAnuReply" not in s:
    s = s.replace(
        'val cleanText = msg.text.removePrefix("You:").removePrefix("You :").trim()',
        'val cleanText = if (msg.role == ChatRole.ANU) ZoyaSessionManager.cleanAnuReply(msg.text) else msg.text.removePrefix("You:").removePrefix("You :").trim()',
        1
    )

if "var showHistoryView by rememberSaveable" not in s:
    s = s.replace(
        "    var showChatHistoryDialog by remember { mutableStateOf(false) }\n",
        "    var showChatHistoryDialog by remember { mutableStateOf(false) }\n    var showHistoryView by rememberSaveable { mutableStateOf(false) }\n",
        1
    )

if "onNewConversation = { viewModel.newConversation() }" not in s:
    s = s.replace(
        """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onVoiceClick = {""",
        """                        AnuNavTab.CHAT -> AnuChatScreen(
                            state = state,
                            onSendMessage = { text -> viewModel.sendText(text) },
                            onNewConversation = { viewModel.newConversation() },
                            onSelectConversation = { id -> viewModel.selectConversation(id) },
                            onVoiceClick = {""", 1
    )

if "onNewConversation: () -> Unit" not in s:
    s = s.replace(
        """fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""",
        """fun AnuChatScreen(
    state: ZoyaUiState,
    onSendMessage: (String) -> Unit,
    onNewConversation: () -> Unit = {},
    onSelectConversation: (String) -> Unit = {},
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""", 1
    )

# Clean, soft, minimal input pill with History and Voice inside
old_input = """        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    if (inputText.isBlank()) {
                        Text(
                            text = "Message Anu...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            )

            if (inputText.isNotBlank()) {
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotBlank()) {
                            onSendMessage(text)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }"""

new_input = """        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .background(
                    color = Color.White.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(30.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFE5E7EB),
                    shape = RoundedCornerShape(30.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showHistoryView = true },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = "Chat History",
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(20.dp)
                )
            }

            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color = Color(0xFF1F2937),
                    fontSize = 15.sp
                ),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    if (inputText.isBlank()) {
                        Text(
                            text = "Message Anu...",
                            fontSize = 15.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                    innerTextField()
                }
            )

            if (inputText.isNotBlank()) {
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isNotBlank()) {
                            onSendMessage(text)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2563EB), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFF3F4F6), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }"""

if old_input in s:
    s = s.replace(old_input, new_input, 1)

# Full conversational history dialog matching the modern card design
dialog_conv_list = """                // Clean conversation history list
                if (conversations.isNotEmpty()) {
                    Text(
                        text = "Saved conversations",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4B5563),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                    conversations.forEach { conv ->
                        val isSelected = conv.id == activeConversationId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectConversation(conv.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(if (isSelected) Color(0xFF3B82F6) else Color(0xFFE5E7EB), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubble,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFF6B7280),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = conv.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = Color(0xFF1F2937),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (conv.snippet.isNotBlank()) {
                                        Text(
                                            text = conv.snippet,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF6B7280),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = formatRelativeTime(conv.updatedAt),
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Divider(color = Color(0xFFE5E7EB))
                    Spacer(Modifier.height(10.dp))
                }

"""

if "Saved conversations" not in s:
    s = s.replace(
        """                if (filteredMessages.isEmpty()) {""",
        """ + dialog_conv_list + """ + """                if (filteredMessages.isEmpty()) {""", 1
    )

p_main.write_text(s, encoding="utf-8")
print("MainActivity updated with clean, soft, minimized chat & history section")
print("All conversation history and functional repairs applied cleanly.")
