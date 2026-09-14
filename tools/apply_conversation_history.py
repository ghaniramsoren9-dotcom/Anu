import os, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
PKG = APP / "src/main/java/com/ghaniram/zoya"

print("Running apply_conversation_history.py...")

# 1. Update AndroidManifest.xml if needed
manifest = APP / "src/main/AndroidManifest.xml"
if manifest.exists():
    s = manifest.read_text(encoding="utf-8")
    if "QUERY_ALL_PACKAGES" not in s:
        s = s.replace(
            "<application",
            '    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />\n    <application'
        )
        manifest.write_text(s, encoding="utf-8")
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

# 3. PhoneControlManager.kt: goHome, enhanced scroll, and multilingual app launcher
p_phone = PKG / "PhoneControlManager.kt"
if p_phone.exists():
    s = p_phone.read_text(encoding="utf-8")
    s = re.sub(r'^\+\s*', '    ', s, flags=re.MULTILINE)
    
    if "fun goHome(): String" not in s:
        go_home_code = """    fun goHome(): String {
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

"""
        s = re.sub(r'(fun accessibilityAction\(action: String)', go_home_code + r'\1', s, count=1)

    new_acc_action = """fun accessibilityAction(action: String, text: String = "", value: String = ""): String {
        val normalized = normalizeAction(action)
        if (normalized == "call" || normalized == "callcontact" || normalized == "callbyname" || normalized == "directcall") return deviceActions.directCall(text)
        if (normalized in listOf("home", "gohome", "homescreen", "openhome")) return goHome()
        val service = AccessibilityControlService.instance
        if (service == null) {
            return if (normalized in listOf("home", "gohome", "homescreen")) goHome()
            else "Anu phone-control accessibility is not enabled. Open Accessibility settings and enable Anu."
        }
        val textToType = if (value.isNotBlank()) value else text
        val ok = when (normalized) {
            "home", "gohome", "back", "goback", "recents", "recentapps", "openrecentapps", "notifications", "opennotifications", "quicksettings", "openquicksettings", "power", "powerdialog", "lock", "lockscreen" -> service.globalAction(action)
            "click", "clicktext" -> service.clickByText(text)
            "longclick", "longclicktext" -> service.clickByText(text, longClick = true)
            "settext", "settextbytext" -> service.setTextByText(text, textToType)
            "typetext", "type", "write", "writetext", "paste", "pastetext", "entertext", "typenote", "typecode", "input", "insert" -> {
                service.typeText(textToType)
            }
            "scroll", "scrollforward", "scrolldown", "down", "swipedown", "swipeup" -> service.scroll(true)
            "scrollbackward", "scrollup", "up" -> service.scroll(false)
            else -> false
        }
        val suffix = if (text.isNotBlank() && normalized !in listOf("typetext", "type", "write", "writetext", "paste", "pastetext", "settext", "settextbytext")) " on $text" else ""
        return if (ok) "completed $action" else "could not complete $action$suffix"
    }

    fun writeNote(title: String, content: String, appName: String = "keep"): String {
        val fullText = if (title.isNotBlank()) "$title\n\n$content" else content
        try {
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(title.ifBlank { "Note" }, fullText))
        } catch (_: Exception) {}

        val service = AccessibilityControlService.instance
        if (service != null) {
            val typed = service.typeText(fullText)
            if (typed) return "Note typed into active editor (${fullText.length} characters)"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, fullText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            app.startActivity(Intent.createChooser(intent, "Save Note with Anu").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            "Note copied to clipboard and sharing opened: '$title' (${fullText.length} characters)"
        } catch (_: Exception) {
            "Note copied to clipboard (${fullText.length} characters): '$title'"
        }
    }

    fun writeCode(code: String, filename: String = "", language: String = ""): String {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return "Code content is empty"

        try {
            val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(filename.ifBlank { "Code" }, cleanCode))
        } catch (_: Exception) {}

        val service = AccessibilityControlService.instance
        if (service != null) {
            val pasted = service.typeText(cleanCode)
            if (pasted) return "Code written into active editor (${cleanCode.length} characters)"
        }
        return "Complete code generated and copied to clipboard (${cleanCode.length} characters)"
    }

    fun githubAction(action: String, repo: String = "", path: String = "", content: String = "", message: String = ""): String {
        val store = AnuSettingsStore.getInstance(app)
        val token = store.githubToken
        if (token.isBlank()) {
            return "GitHub is not connected yet. Please connect your GitHub account in Anu Settings > Connectors."
        }
        val targetRepo = if (repo.isNotBlank()) repo else store.githubDefaultRepo
        return GitHubConnectorClient.executeAction(token, action, targetRepo, path, content, message)
    }

    fun openAccessibilitySettings"""
    s = re.sub(r'fun accessibilityAction\(action: String.*?\n    fun openAccessibilitySettings', new_acc_action, s, count=1, flags=re.DOTALL)

    new_open_app = """fun openApp(appName: String): String {
        if (appName.isBlank()) return "app name is missing"
        val clean = appName.trim().lowercase()
            .replace(Regex("^(open|launch|start|run|ଖୋଲ|khola|kholo)\\\\s+"), "")
            .replace(Regex("\\\\s+(open|kholo|khola|ଖୋଲ|app)$"), "")
            .trim()
        val target = if (clean.isNotBlank()) clean else appName.trim().lowercase()
        val pm = app.packageManager
        when (target) {
            "camera", "କ୍ୟାମେରା", "कैमरा" -> return openCamera()
            "settings", "setting", "ସେଟିଙ୍ଗ୍", "ସେଟିଂ", "सेटिंग्स" -> return openSettings()
            "phone", "dialer", "ଫୋନ୍", "କଲ୍", "फोन" -> return openPhone()
            "messages", "sms", "ମେସେଜ୍", "मैसेज" -> return openMessages()
            "wifi", "wifi settings" -> return openWifiSettings()
            "bluetooth", "bluetooth settings" -> return openBluetoothSettings()
            "sound", "sound settings" -> return openSoundSettings()
            "display", "display settings" -> return openDisplaySettings()
        }
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube", "yt" to "com.google.android.youtube", "ୟୁଟ୍ୟୁବ୍" to "com.google.android.youtube", "ୟୁଟ୍ୟୁବ" to "com.google.android.youtube", "यूट्यूब" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp", "wa" to "com.whatsapp", "ହ୍ଵାଟ୍ସଆପ୍" to "com.whatsapp", "ହ୍ୱାଟ୍ସଆପ" to "com.whatsapp", "व्हाट्सएप" to "com.whatsapp",
            "instagram" to "com.instagram.android", "insta" to "com.instagram.android", "ଇନଷ୍ଟାଗ୍ରାମ୍" to "com.instagram.android", "इंस्टाग्राम" to "com.instagram.android",
            "facebook" to "com.facebook.katana", "fb" to "com.facebook.katana", "ଫେସବୁକ୍" to "com.facebook.katana", "फेसबुक" to "com.facebook.katana",
            "messenger" to "com.facebook.orca",
            "chrome" to "com.android.chrome", "browser" to "com.android.chrome", "କ୍ରୋମ୍" to "com.android.chrome", "କ୍ରୋମ" to "com.android.chrome", "क्रोम" to "com.android.chrome",
            "gmail" to "com.google.android.gm", "email" to "com.google.android.gm", "ମେଲ୍" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps", "googlemaps" to "com.google.android.apps.maps", "ମ୍ୟାପ୍" to "com.google.android.apps.maps",
            "photos" to "com.google.android.apps.photos", "gallery" to "com.google.android.apps.photos", "ଫଟୋ" to "com.google.android.apps.photos", "ଗ୍ୟାଲେରି" to "com.google.android.apps.photos",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger", "ଟେଲିଗ୍ରାମ୍" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "netflix" to "com.netflix.mediaclient",
            "playstore" to "com.android.vending", "store" to "com.android.vending",
            "clock" to "com.google.android.deskclock", "calculator" to "com.google.android.calculator"
        )
        val knownPkg = knownPackages[target] ?: knownPackages[normalize(target)]
        if (knownPkg != null && launchPackage(pm, knownPkg)) return "opened $appName"
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = try { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL) } catch (_: Exception) { emptyList() }
        val exact = matches.firstOrNull { normalize(it.loadLabel(pm).toString()) == normalize(target) }
        val fuzzy = exact ?: matches.firstOrNull { info -> val lbl = normalize(info.loadLabel(pm).toString()); lbl.contains(normalize(target)) || normalize(target).contains(lbl) }
        if (fuzzy != null) {
            val launch = pm.getLaunchIntentForPackage(fuzzy.activityInfo.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                app.startActivity(launch)
                return "opened ${fuzzy.loadLabel(pm)}"
            }
        }
        if (target.contains("youtube") || target.contains("ୟୁଟ୍ୟୁବ")) {
            return try { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "opened YouTube" } catch (_: Exception) { "could not open YouTube" }
        }
        if (target.contains("whatsapp") || target.contains("ହ୍ଵାଟ୍ସ")) {
            return try { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "opened WhatsApp" } catch (_: Exception) { "could not open WhatsApp" }
        }
        return "could not find or open app: $appName"
    }"""
    s = re.sub(r'fun openApp\(appName: String\): String\s*\{.*?\n    private fun launchPackage', new_open_app, s, count=1, flags=re.DOTALL)

    p_phone.write_text(s, encoding="utf-8")
    print("PhoneControlManager updated with goHome, openApp, and accessibilityAction")

# 4. GeminiLiveClient.kt: fix sendText to use clientContent
p_live = PKG / "GeminiLiveClient.kt"
if p_live.exists():
    s = p_live.read_text(encoding="utf-8")
    old_client_send = """        val textPart = JSONObject().put("text", text)
        val msg = JSONObject().put("realtimeInput", JSONObject().put("mediaChunks", JSONArray().put(textPart)))"""
    new_client_send = """        val textPart = JSONObject().put("text", text)
        val msg = JSONObject().put("clientContent", JSONObject().put("turns", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(textPart)))).put("turnComplete", true))"""
    if old_client_send in s:
        s = s.replace(old_client_send, new_client_send)
        p_live.write_text(s, encoding="utf-8")
        print("GeminiLiveClient sendText updated")

# 5. Models.kt: add AnuConversationSummary
p_models = PKG / "Models.kt"
if p_models.exists():
    s = p_models.read_text(encoding="utf-8")
    if "data class AnuConversationSummary" not in s:
        summary_model = """data class AnuConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: Long,
    val snippet: String
)

"""
        s = summary_model + s
        if "val chatConversations: List<AnuConversationSummary>" not in s:
            s = s.replace(
                "val chatMessages: List<ChatMessage> = emptyList(),",
                "val chatMessages: List<ChatMessage> = emptyList(),\n    val chatConversations: List<AnuConversationSummary> = emptyList(),\n    val activeConversationId: String = \"default\","
            )
        p_models.write_text(s, encoding="utf-8")
        print("Models.kt updated with AnuConversationSummary")

# 6. ZoyaSessionManager.kt: conversations, direct action, tone cleanup, executeTool
p_session = PKG / "ZoyaSessionManager.kt"
if p_session.exists():
    s = p_session.read_text(encoding="utf-8")
    
    # Helper functions
    helpers = """    private const val CONVERSATION_MARKER = "__ANU_CONVERSATION__"
    private const val ACTIVE_CONVERSATION_PREF = "active_conversation_id"
    private fun conversationMarker(id: String) = ChatMessage(UUID.randomUUID().toString(), ChatRole.SYSTEM, "$CONVERSATION_MARKER|$id", System.currentTimeMillis())

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
        res = res.replace(Regex("<thought>[\\\\s\\\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("<tone[;:][^>]+>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("<(emotion|style|mood|gesture|action)[;:][^>]+>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("<[a-zA-Z0-9_-]+[;:][^>]+>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("</?(whisper|sigh|gasp|laughter|chuckle|pause)>", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("\\\\[(SYSTEM ACTION|DIRECT ACTION|ACTION|TOOL)[^\\\\]]*\\\\]", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("^(Anu|Assistant|You)\\\\s*:\\\\s*", RegexOption.IGNORE_CASE), "")
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
    if "CONVERSATION_MARKER" not in s:
        s = re.sub(r'object ZoyaSessionManager\s*\{', 'object ZoyaSessionManager {\n' + helpers, s, count=1)

    # Clean tone tags in onModelText (e.g. <tone:warm>)
    if "val clean = cleanAnuReply" not in s:
        old_model_handler = """            override fun onModelText(text: String) {
                if (!isCurrentSession()) return
                val clean = text.removePrefix("You:").removePrefix("You :").trim()"""
        new_model_handler = """            override fun onModelText(text: String) {
                if (!isCurrentSession()) return
                val clean = cleanAnuReply(text.removePrefix("You:").removePrefix("You :"))"""
        if old_model_handler in s:
            s = s.replace(old_model_handler, new_model_handler, 1)

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

    # Direct command execution in sendText
    if "tryExecuteDirectAction" in s and "val directResult = tryExecuteDirectAction(clean)" not in s[s.find("fun sendText"):s.find("val proactive =")]:
        old_send = """    fun sendText(text: String) {
        ensureInitialized()
        val clean = text.trim()
        if (clean.isBlank()) return"""
        new_send = """    fun sendText(text: String) {
        ensureInitialized()
        val clean = text.trim()
        if (clean.isBlank()) return

        val directResult = tryExecuteDirectAction(clean)
        if (directResult != null) {
            val userMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
            val anuMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, directResult, System.currentTimeMillis() + 10L)
            _state.update { it.copy(chatMessages = it.chatMessages + userMsg + anuMsg, isAnuResponding = false) }
            scope.launch { repository.saveChatMessage(userMsg); repository.saveChatMessage(anuMsg) }
            if (isConnected()) client?.sendText("[SYSTEM ACTION COMPLETED: $directResult] Acknowledge concisely to the user in ${_state.value.language.label}.")
            return
        }"""
        if old_send in s:
            s = s.replace(old_send, new_send, 1)

    # Ensure repository load fills chatConversations
    if "conversationSummaries(messages)" not in s:
        s = s.replace(
            "repository.allChatMessagesFlow.collect { messages -> _state.update { it.copy(chatMessages = messages) } }",
            """repository.allChatMessagesFlow.collect { messages ->
                val activeId = prefs.getString(ACTIVE_CONVERSATION_PREF, "default") ?: "default"
                _state.update { it.copy(
                    chatMessages = messagesForConversation(messages, activeId),
                    chatConversations = conversationSummaries(messages),
                    activeConversationId = activeId
                ) }
            }"""
        )

    p_session.write_text(s, encoding="utf-8")
    print("ZoyaSessionManager.kt updated with conversation splits, direct actions, and tone cleanup")

# 7. MainActivity.kt: Add onNewConversation, selectConversation, conversation state to UI
p_main = PKG / "MainActivity.kt"
if p_main.exists():
    s = p_main.read_text(encoding="utf-8")

    # Fix time-based greeting on Home Screen
    old_greeting = """            val currentGreeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Good night"
            }"""
    new_greeting = """            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val currentGreeting = when {
                currentHour in 5..11 -> "Good morning"
                currentHour in 12..16 -> "Good afternoon"
                currentHour in 17..21 -> "Good evening"
                else -> "Good night"
            }
            val userName = remember(context) {
                runCatching { AnuSettingsStore.getInstance(context).userName }.getOrNull()?.trim()?.ifBlank { "Ghaniram" } ?: "Ghaniram"
            }"""
    if old_greeting in s and "val currentHour" not in s:
        s = s.replace(old_greeting, new_greeting, 1)

    # Clean displayed text in Chat message bubble (remove <tone:warm>)
    if "ZoyaSessionManager.cleanAnuReply" not in s:
        s = s.replace(
            'val cleanText = msg.text.removePrefix("You:").removePrefix("You :").trim()',
            'val cleanText = if (msg.role == ChatRole.ANU) ZoyaSessionManager.cleanAnuReply(msg.text) else msg.text.removePrefix("You:").removePrefix("You :").trim()',
            1
        )

    # Wire chat dialog callbacks
    if "conversations = state.chatConversations" not in s:
        old_dialog_call = """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            onDismiss = { showChatHistoryDialog = false },
            onClear = onClearChat
        )"""
        new_dialog_call = """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            conversations = state.chatConversations,
            onSelectConversation = { id -> onSelectConversation(id); showChatHistoryDialog = false },
            onNewConversation = { onNewConversation(); showChatHistoryDialog = false },
            onDismiss = { showChatHistoryDialog = false },
            onClear = {
                onClearChat()
                showChatHistoryDialog = false
            }
        )"""
        if old_dialog_call in s:
            s = s.replace(old_dialog_call, new_dialog_call, 1)

    # Wire AnuChatScreen params in Home/Nav host
    if "onNewConversation = {" not in s:
        s = s.replace(
            """                            onSendMessage = { text -> viewModel.sendText(text) },
                            onVoiceClick = {""",
            """                            onSendMessage = { text -> viewModel.sendText(text) },
                            onNewConversation = { ZoyaSessionManager.newConversation() },
                            onSelectConversation = { id -> ZoyaSessionManager.selectConversation(id) },
                            onVoiceClick = {""", 1
        )

    if "onNewConversation: () -> Unit" not in s:
        s = s.replace(
            """fun AnuChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,""",
            """fun AnuChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onNewConversation: () -> Unit = {},
    onSelectConversation: (String) -> Unit = {},""", 1
        )

    p_main.write_text(s, encoding="utf-8")
    print("MainActivity.kt updated with dynamic greeting, tone cleanup, and conversation integration")

print("apply_conversation_history.py finished successfully!")
