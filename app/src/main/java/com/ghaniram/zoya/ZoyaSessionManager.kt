package com.ghaniram.zoya

import android.content.Intent
import android.net.Uri
import android.app.Application
import androidx.core.content.ContextCompat
import com.ghaniram.zoya.data.local.AnuDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ZoyaSessionManager {
    private lateinit var app: Application
    private val _state = MutableStateFlow(ZoyaUiState())
    val state: StateFlow<ZoyaUiState> = _state
    private var client: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    private var modelSpeaking = false
    private var initialized = false
    private val memoryStore by lazy { MemoryStore(app) }
    private val repository by lazy { AnuDataRepository.getInstance(app) }
    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val phoneControls by lazy { PhoneControlManager(app) }
    private val prefs by lazy { app.getSharedPreferences("anu_session", 0) }
    private val idleQuotes = mapOf(
        ZoyaLanguage.ODIA to listOf("କୁହ କଣ ହେଲା? ମୁଁ ସବୁ ଶୁଣୁଛି।", "ମୋ ସହିତ ଓଡ଼ିଆରେ କଥା ହୁଅ!", "କଣ କରିବା ଦରକାର କୁହ, ମୁଁ ପ୍ରସ୍ତୁତ।"),
        ZoyaLanguage.ENGLISH to listOf("Go ahead, I'm listening.", "What can I do for you?", "I'm ready when you are."),
        ZoyaLanguage.HINDI to listOf("बोलिए, मैं सुन रही हूँ।", "आपके लिए क्या करूँ?"),
        ZoyaLanguage.SANTALI to listOf("ᱤᱧ ᱥᱟᱶ ᱜᱟᱞᱢᱟᱨᱟᱣ ᱢᱮ ᱾")
    )

    fun initialize(application: Application) {
        app = application
        if (!initialized) {
            initialized = true
            val savedLanguage = runCatching {
                ZoyaLanguage.valueOf(prefs.getString("language", ZoyaLanguage.ODIA.name) ?: ZoyaLanguage.ODIA.name)
            }.getOrDefault(ZoyaLanguage.ODIA)
            _state.value = ZoyaUiState(language = savedLanguage, quote = idleQuotes[savedLanguage]?.random() ?: "")
            sessionScope.launch {
                runCatching {
                    val legacyMemories = memoryStore.getAll()
                    if (legacyMemories.isNotEmpty()) {
                        legacyMemories.forEach { repository.saveMemory(it) }
                        memoryStore.clear()
                    }
                }
            }
            sessionScope.launch {
                repository.allMemoriesFlow.collect { memories ->
                    _state.update { it.copy(memories = memories) }
                }
            }
            sessionScope.launch {
                repository.allChatMessagesFlow.collect { messages ->
                    _state.update { it.copy(chatMessages = messages) }
                }
            }
        }
    }

    fun setLanguage(lang: ZoyaLanguage) {
        ensureInitialized()
        prefs.edit().putString("language", lang.name).apply()
        _state.update { it.copy(language = lang, quote = idleQuotes[lang]?.random() ?: "") }
        if (isConnected()) reconnect()
    }

    fun connect() {
        ensureInitialized()
        prefs.edit().putBoolean("active", true).apply()
        startForegroundService()
        connectInternal()
    }

    fun disconnect() {
        ensureInitialized()
        prefs.edit().putBoolean("active", false).apply()
        client?.disconnect()
        audioEngine?.release()
        client = null
        audioEngine = null
        modelSpeaking = false
        stopForegroundService()
        _state.update {
            it.copy(
                connectionState = ConnectionState.DISCONNECTED,
                inputLevel = 0f,
                outputLevel = 0f,
                isAnuResponding = false
            )
        }
    }

    fun startVisionSession() {
        ensureInitialized()
        prefs.edit().putBoolean("active", true).apply()
        startForegroundService()
        if (!isConnected()) connectInternal()
    }

    fun sendVisionFrame(base64Jpeg: String) {
        ensureInitialized()
        if (base64Jpeg.isNotBlank() && isConnected()) client?.sendVideoFrame(base64Jpeg)
    }

    fun restoreIfNeeded() {
        ensureInitialized()
        if (prefs.getBoolean("active", false) && !isConnected()) connectInternal()
    }

    fun setVisionActive(active: Boolean) {
        _state.update { it.copy(isVisionActive = active) }
    }

    fun setVisionDescription(description: String) {
        _state.update { it.copy(visionDescription = description) }
    }

    fun analyzeVisionFrame(jpegBytes: ByteArray, prompt: String, onComplete: ((String) -> Unit)? = null) {
        ensureInitialized()
        if (jpegBytes.isEmpty()) return
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            val fallback = "Gemini API key is not configured. Please add it in Settings."
            _state.update { it.copy(visionDescription = fallback, error = fallback) }
            onComplete?.invoke(fallback)
            return
        }
        GeminiVisionClient(apiKey).analyze(jpegBytes, prompt, object : GeminiVisionClient.Callback {
            override fun onSuccess(text: String) {
                _state.update { it.copy(visionDescription = text) }
                onComplete?.invoke(text)
            }
            override fun onError(message: String) {
                val err = "Camera vision error: $message"
                _state.update { it.copy(visionDescription = err) }
                onComplete?.invoke(err)
            }
        })
    }

    fun sendText(text: String) {
        ensureInitialized()
        val clean = text.trim()
        if (clean.isBlank()) return
        val userMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
        _state.update { it.copy(chatMessages = it.chatMessages + userMsg, error = null, isAnuResponding = true) }
        sessionScope.launch { repository.saveChatMessage(userMsg) }
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            val botMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, "Please add your Gemini API key in Settings.", System.currentTimeMillis())
            _state.update { s -> s.copy(chatMessages = s.chatMessages + botMsg, isAnuResponding = false) }
            sessionScope.launch { repository.saveChatMessage(botMsg) }
            return
        }
        if (!isConnected()) connectInternal()
        client?.sendText(clean)
    }

    fun addTask(title: String, time: String) {
        val task = AnuTask(UUID.randomUUID().toString(), title, time, false)
        _state.update { it.copy(tasks = it.tasks + task) }
    }

    fun toggleTask(id: String) {
        _state.update { s ->
            s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it })
        }
    }

    fun deleteTask(id: String) {
        _state.update { s -> s.copy(tasks = s.tasks.filterNot { it.id == id }) }
    }

    fun clearMemories() {
        sessionScope.launch { repository.clearMemories() }
    }

    fun clearChatHistory() {
        sessionScope.launch { repository.clearChatMessages() }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun onApiKeyUpdated(newKey: String) {
        if (newKey.isNotBlank()) {
            _state.update {
                if (it.error?.contains("API", ignoreCase = true) == true || it.error?.contains("key", ignoreCase = true) == true)
                    it.copy(error = null) else it
            }
        }
    }

    fun onSettingsUpdated() {
        if (isConnected()) reconnect()
    }

    private fun connectInternal() {
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            _state.update { it.copy(error = "Gemini API key is not configured", connectionState = ConnectionState.DISCONNECTED) }
            return
        }
        client?.disconnect()
        audioEngine?.release()
        modelSpeaking = false
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING, error = null, inputLevel = 0f, outputLevel = 0f) }
        audioEngine = AudioEngine(
            onMicChunkBase64 = { chunk -> if (!modelSpeaking) client?.sendAudioChunk(chunk) },
            onInputLevel = { level -> _state.update { it.copy(inputLevel = level) } },
            onOutputLevel = { level -> _state.update { s -> s.copy(outputLevel = level, connectionState = if (level > 0.03f) ConnectionState.SPEAKING else s.connectionState) } }
        )
        val voiceSpeaker = settings?.selectedVoiceSpeaker?.takeIf { it.isNotBlank() } ?: "Kore"
        client = GeminiLiveClient(apiKey = apiKey, voiceName = voiceSpeaker, callbacks = object : GeminiLiveClient.Callbacks {
            override fun onConnected() {
                audioEngine?.startPlayback()
                audioEngine?.startRecording()
                modelSpeaking = false
                _state.update { it.copy(connectionState = ConnectionState.LISTENING) }
            }
            override fun onDisconnected() {
                modelSpeaking = false
                audioEngine?.stopRecording()
                if (prefs.getBoolean("active", false)) _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
                else _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
            }
            override fun onError(message: String) {
                modelSpeaking = false
                audioEngine?.stopRecording()
                _state.update { it.copy(error = message, connectionState = ConnectionState.DISCONNECTED, isAnuResponding = false) }
            }
            override fun onAudioChunk(base64Pcm: String) {
                if (!modelSpeaking) {
                    modelSpeaking = true
                    audioEngine?.stopRecording()
                    _state.update { it.copy(connectionState = ConnectionState.SPEAKING) }
                }
                audioEngine?.playChunkBase64(base64Pcm)
            }
            override fun onUserText(text: String) {
                val clean = text.removePrefix("You:").removePrefix("You :").trim()
                if (clean.isBlank()) return
                val newMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
                _state.update { s -> s.copy(chatMessages = s.chatMessages + newMsg) }
                sessionScope.launch { repository.saveChatMessage(newMsg) }
            }
            override fun onModelText(text: String) {
                val clean = text.removePrefix("You:").removePrefix("You :").trim()
                if (clean.isBlank()) return
                val newMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, clean, System.currentTimeMillis())
                _state.update { s -> s.copy(chatMessages = s.chatMessages + newMsg, isAnuResponding = false) }
                sessionScope.launch { repository.saveChatMessage(newMsg) }
            }
            override fun onInterrupted() {
                modelSpeaking = false
                audioEngine?.flushPlayback()
                audioEngine?.startRecording()
                _state.update { it.copy(connectionState = ConnectionState.LISTENING, outputLevel = 0f, isAnuResponding = false) }
            }
            override fun onTurnComplete() {
                _state.update { it.copy(isAnuResponding = false) }
                audioEngine?.whenPlaybackDrained {
                    if (prefs.getBoolean("active", false) && !modelSpeaking) {
                        audioEngine?.startRecording()
                        _state.update { it.copy(connectionState = ConnectionState.LISTENING) }
                    }
                }
            }
            override fun onToolCall(name: String, args: JSONObject, id: String) = handleToolCall(name, args, id)
        })
        client?.connect(buildSystemInstruction(), buildToolDeclarations())
    }

    private fun handleToolCall(name: String, args: JSONObject, id: String) {
        val result = when (name) {
            "openWebsite" -> {
                val url = args.optString("url")
                val label = args.optString("name", url)
                if (url.isBlank()) "invalid URL"
                else runCatching {
                    app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    "opened $label"
                }.getOrElse { "could not open $label: ${it.message ?: "unknown error"}" }
            }
            "openApp" -> phoneControls.openApp(args.optString("appName"))
            "phoneAction" -> runCatching {
                when (args.optString("action").lowercase().trim()) {
                    "phone" -> phoneControls.openPhone()
                    "messages" -> phoneControls.openMessages()
                    "camera" -> phoneControls.openCamera()
                    "settings" -> phoneControls.openSettings()
                    "wifi_settings", "wifi" -> phoneControls.openWifiSettings()
                    "bluetooth_settings", "bluetooth" -> phoneControls.openBluetoothSettings()
                    "flashlight_on" -> phoneControls.flashlight(true)
                    "flashlight_off" -> phoneControls.flashlight(false)
                    "volume_up" -> phoneControls.volumeUp()
                    "volume_down" -> phoneControls.volumeDown()
                    else -> phoneControls.openSettings()
                }
            }.getOrElse { "phone action failed: ${it.message}" }
            "accessibilityAction" -> phoneControls.accessibilityAction(
                args.optString("action"), args.optString("text"), args.optString("value")
            )
            "getScreenInfo" -> {
                val service = AccessibilityControlService.instance
                if (service == null) "Accessibility Service is not enabled"
                else service.uiSnapshot()
            }
            "getDeviceInfo" -> DeviceInfoProvider.snapshot(app)
            "readNotifications" -> {
                val service = ZoyaNotificationListenerService.instance
                if (service == null) "Notification Access is not enabled for Anu."
                else {
                    val items = service.readActiveNotifications()
                    if (items.isEmpty()) "There are no readable active notifications." else items.take(30).joinToString("\n")
                }
            }
            "replyToNotification" -> {
                val service = ZoyaNotificationListenerService.instance
                if (service == null) "Notification Access is not enabled for Anu."
                else service.replyToNotification(args.optString("query"), args.optString("replyText"))
            }
            "sendEmail" -> {
                val to = args.optString("to")
                val subject = args.optString("subject", "Message from Anu")
                val body = args.optString("body")
                val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
                if (settings == null) "Email settings unavailable"
                else EmailSender.send(settings, to, subject, body).message
            }
            "savePersonalFact" -> {
                val fact = args.optString("fact")
                if (fact.isNotBlank()) sessionScope.launch { repository.saveMemory(fact) }
                "saved"
            }
            "forgetPersonalFact" -> {
                val fact = args.optString("fact")
                sessionScope.launch { repository.removeMemory(fact) }
                "forgotten"
            }
            else -> "unsupported"
        }
        client?.sendToolResponse(name, id, result)
    }

    private fun buildSystemInstruction(): String {
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val assistantName = settings?.assistantName ?: "Anu"
        return "You are $assistantName, a confident, helpful AI assistant. Keep voice answers concise and natural. " +
            "ALWAYS call openApp for app-opening requests. ALWAYS call phoneAction for phone controls. " +
            "ALWAYS call accessibilityAction for home, back, recents, click, type text, scrolling. " +
            "ALWAYS call getScreenInfo when asked what is on the screen. ALWAYS call getDeviceInfo for battery/device info. " +
            "ALWAYS call readNotifications when asked to read notifications. " +
            "ALWAYS call sendEmail when the user asks to send an email (requires Email settings). " +
            "Never claim an action succeeded unless the tool returned success."
    }

    private fun buildToolDeclarations(): JSONArray = JSONArray().apply {
        put(functionDeclaration("openWebsite", "Open a website URL.", JSONObject().apply {
            put("url", property("STRING", "Full URL.")); put("name", property("STRING", "Friendly name."))
        }, JSONArray().put("url")))
        put(functionDeclaration("openApp", "Launch an installed Android app by name.",
            JSONObject().put("appName", property("STRING", "Visible app name.")), JSONArray().put("appName")))
        put(functionDeclaration("phoneAction", "Perform phone and system controls.",
            JSONObject().put("action", property("STRING", "Action name such as wifi_settings, flashlight_on, volume_up.")),
            JSONArray().put("action")))
        put(functionDeclaration("accessibilityAction", "Perform an Accessibility action.", JSONObject().apply {
            put("action", property("STRING", "Action name.")); put("text", property("STRING", "Visible text.")); put("value", property("STRING", "Value to set."))
        }, JSONArray().put("action")))
        put(functionDeclaration("getScreenInfo", "Read current screen accessibility structure.", JSONObject(), JSONArray()))
        put(functionDeclaration("getDeviceInfo", "Read device telemetry.", JSONObject(), JSONArray()))
        put(functionDeclaration("readNotifications", "Read active notifications.", JSONObject(), JSONArray()))
        put(functionDeclaration("replyToNotification", "Reply to a notification.", JSONObject().apply {
            put("query", property("STRING", "Notification search text.")); put("replyText", property("STRING", "Reply text."))
        }, JSONArray().put("query").put("replyText")))
        put(functionDeclaration("sendEmail", "Send an email using the configured SMTP account (Gmail App Password).", JSONObject().apply {
            put("to", property("STRING", "Recipient email address.")); put("subject", property("STRING", "Email subject.")); put("body", property("STRING", "Email body text."))
        }, JSONArray().put("to").put("body")))
        put(functionDeclaration("savePersonalFact", "Save a personal memory.",
            JSONObject().put("fact", property("STRING", "Fact to remember.")), JSONArray().put("fact")))
        put(functionDeclaration("forgetPersonalFact", "Forget a personal memory.",
            JSONObject().put("fact", property("STRING", "Fact to forget.")), JSONArray().put("fact")))
    }

    private fun functionDeclaration(name: String, description: String, properties: JSONObject, required: JSONArray) =
        JSONObject().put("name", name).put("description", description)
            .put("parameters", JSONObject().put("type", "object").put("properties", properties).put("required", required))

    private fun property(type: String, description: String) =
        JSONObject().put("type", type.lowercase()).put("description", description)

    private fun ensureInitialized() { check(initialized) { "ZoyaSessionManager is not initialized" } }
    private fun isConnected(): Boolean = client != null
    private fun reconnect() { client?.disconnect(); connectInternal() }
    private fun startForegroundService() {
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, ZoyaForegroundService::class.java))
        }
    }
    private fun stopForegroundService() {
        runCatching { app.stopService(Intent(app, ZoyaForegroundService::class.java)) }
    }
}
