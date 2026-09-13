from pathlib import Path
import re

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
        val ok = when (normalized) {
            "home", "gohome", "back", "goback", "recents", "recentapps", "openrecentapps", "notifications", "opennotifications", "quicksettings", "openquicksettings", "power", "powerdialog", "lock", "lockscreen" -> service.globalAction(action)
            "click", "clicktext" -> service.clickByText(text)
            "longclick", "longclicktext" -> service.clickByText(text, longClick = true)
            "settext", "settextbytext" -> service.setTextByText(text, value)
            "typetext", "type" -> service.typeText(value)
            "scroll", "scrollforward", "scrolldown", "down", "swipedown", "swipeup" -> service.scroll(true)
            "scrollbackward", "scrollup", "up" -> service.scroll(false)
            else -> false
        }
        val suffix = if (text.isNotBlank()) " on $text" else ""
        return if (ok) "completed $action" else "could not complete $action$suffix"
    }
    fun openAccessibilitySettings"""
    s = re.sub(r'fun accessibilityAction\(action: String.*?\n\s*fun openAccessibilitySettings', new_acc_action, s, count=1, flags=re.DOTALL)

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
    }
    private fun launchPackage"""
    s = re.sub(r'fun openApp\(appName: String\): String\s*\{.*?\n\s*private fun launchPackage', new_open_app, s, count=1, flags=re.DOTALL)

    p_phone.write_text(s, encoding="utf-8")
    print("PhoneControlManager updated with goHome, openApp, and accessibilityAction")

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
    val updatedAt: Long
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
    helper = """    private fun conversationMarker(id: String) = ChatMessage(UUID.randomUUID().toString(), ChatRole.SYSTEM, "$CONVERSATION_MARKER|$id", System.currentTimeMillis())

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
            val title = first?.text?.replace("\\n", " ")?.trim()?.take(36)?.ifBlank { "New Chat" } ?: "Chat"
            val updated = items.maxOfOrNull { it.timestampMillis } ?: 0L
            AnuConversationSummary(id, title, items.size, updated)
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
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages -> _state.update { it.copy(chatMessages = messages) } } } }'''
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
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onClearChat: () -> Unit
) {""", 1
    )

# Soft, clean, minimal header buttons: History button and New button
old_box = """            Box {
                IconButton(onClick = { showMenu = true }) {"""

new_box = """            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showChatHistoryDialog = true }) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = AnuPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("History", color = AnuPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onNewConversation) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = AnuPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("New", color = AnuPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box {
                IconButton(onClick = { showMenu = true }) {"""

old_close = """            }
        }

        // Chat Message List or Empty Placeholder"""

new_close = """                }
            }
        }

        // Chat Message List or Empty Placeholder"""

if old_box in s:
    s = s.replace(old_box, new_box, 1)
    s = s.replace(old_close, new_close, 1)

# History Dialog: update signature and provide clean, soft, beautiful Saved conversations list
if "conversations: List<AnuConversationSummary>" not in s:
    s = s.replace(
        """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""",
        """fun AnuChatHistoryDialog(
    messages: List<ChatMessage>,
    conversations: List<AnuConversationSummary>,
    activeConversationId: String,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {""", 1
    )

if "conversations = state.chatConversations" not in s:
    s = s.replace(
        """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            onDismiss = { showChatHistoryDialog = false },""",
        """        AnuChatHistoryDialog(
            messages = state.chatMessages,
            conversations = state.chatConversations,
            activeConversationId = state.activeConversationId,
            onSelectConversation = { id -> onSelectConversation(id); showChatHistoryDialog = false },
            onNewConversation = { onNewConversation(); showChatHistoryDialog = false },
            onDismiss = { showChatHistoryDialog = false },""", 1
    )

if "val filteredConversations =" not in s:
    s = s.replace(
        """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
""",
        """    val filteredMessages = remember(messages, searchQuery) {
        if (searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
""", 1
    )

if "Saved conversations" not in s:
    dialog_conv_list = '''                    Text("Saved conversations", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
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
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = AnuPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(conversation.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AnuTextDark, maxLines = 1)
                                            Text("${conversation.messageCount} messages", fontSize = 9.5.sp, color = AnuTextMuted, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No saved conversations yet.", fontSize = 11.5.sp, color = AnuTextMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onNewConversation) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(16.dp), tint = AnuPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("New conversation", color = AnuPrimary, fontSize = 11.5.sp)
                        }
                    }
                    Text("Messages in current conversation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AnuTextMuted)
                    Spacer(Modifier.height(6.dp))
'''
    s = s.replace(
        """                    Spacer(Modifier.height(10.dp))

                    if (filteredMessages.isEmpty()) {""",
        """                    Spacer(Modifier.height(10.dp))

""" + dialog_conv_list + """                    if (filteredMessages.isEmpty()) {""", 1
    )

p_main.write_text(s, encoding="utf-8")
print("MainActivity updated with clean, soft, minimized chat & history section")

print("All conversation history and functional repairs applied cleanly.")
