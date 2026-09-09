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
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// NOTE: Full SessionManager body is large; this push restores structure.
// If incomplete, pull from local artifacts/anu-phase1/ZoyaSessionManager.kt
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

    fun initialize(application: Application) {
        app = application
        if (!initialized) {
            initialized = true
            _state.value = ZoyaUiState()
        }
    }

    fun connect() { ensureInitialized(); prefs.edit().putBoolean("active", true).apply(); startForegroundService(); connectInternal() }
    fun disconnect() { ensureInitialized(); prefs.edit().putBoolean("active", false).apply(); client?.disconnect(); audioEngine?.release(); client = null; audioEngine = null; modelSpeaking = false; stopForegroundService(); _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) } }
    fun sendText(text: String) {
        ensureInitialized()
        val clean = text.trim()
        if (clean.isBlank()) return
        if (!isConnected()) connectInternal()
        client?.sendText(clean)
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
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING, error = null) }
        audioEngine = AudioEngine(
            onMicChunkBase64 = { chunk -> if (!modelSpeaking) client?.sendAudioChunk(chunk) },
            onInputLevel = { level -> _state.update { it.copy(inputLevel = level) } },
            onOutputLevel = { level -> _state.update { s -> s.copy(outputLevel = level) } }
        )
        val voiceSpeaker = settings?.selectedVoiceSpeaker?.takeIf { it.isNotBlank() } ?: "Kore"
        client = GeminiLiveClient(apiKey = apiKey, voiceName = voiceSpeaker, callbacks = object : GeminiLiveClient.Callbacks {
            override fun onConnected() {
                audioEngine?.startPlayback()
                audioEngine?.startRecording()
                modelSpeaking = false
                _state.update { it.copy(connectionState = ConnectionState.LISTENING) }
            }
            override fun onDisconnected() { modelSpeaking = false; audioEngine?.stopRecording(); _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED) } }
            override fun onError(message: String) { modelSpeaking = false; audioEngine?.stopRecording(); _state.update { it.copy(error = message, connectionState = ConnectionState.DISCONNECTED) } }
            override fun onAudioChunk(base64Pcm: String) {
                if (!modelSpeaking) { modelSpeaking = true; audioEngine?.stopRecording(); _state.update { it.copy(connectionState = ConnectionState.SPEAKING) } }
                audioEngine?.playChunkBase64(base64Pcm)
            }
            override fun onUserText(text: String) {}
            override fun onModelText(text: String) {}
            override fun onInterrupted() { modelSpeaking = false; audioEngine?.flushPlayback(); audioEngine?.startRecording(); _state.update { it.copy(connectionState = ConnectionState.LISTENING) } }
            override fun onTurnComplete() { _state.update { it.copy(isAnuResponding = false) } }
            override fun onToolCall(name: String, args: JSONObject, id: String) = handleToolCall(name, args, id)
        })
        client?.connect(buildSystemInstruction(), buildToolDeclarations())
    }

    private fun handleToolCall(name: String, args: JSONObject, id: String) {
        val result = when (name) {
            "openWebsite" -> {
                val url = args.optString("url")
                if (url.isBlank()) "invalid URL" else runCatching {
                    app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    "opened $url"
                }.getOrElse { "could not open: ${it.message}" }
            }
            "openApp" -> phoneControls.openApp(args.optString("appName"))
            "sendEmail" -> {
                val to = args.optString("to")
                val subject = args.optString("subject", "Message from Anu")
                val body = args.optString("body")
                val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
                if (settings == null) "Email settings unavailable"
                else EmailSender.send(settings, to, subject, body).message
            }
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
            "getDeviceInfo" -> DeviceInfoProvider.snapshot(app)
            "getScreenInfo" -> {
                val service = AccessibilityControlService.instance
                if (service == null) "Accessibility Service is not enabled"
                else service.uiSnapshot()
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
        return "You are $assistantName, a confident, helpful AI assistant. Keep voice answers concise. ALWAYS call sendEmail when the user asks to send an email (requires Email settings). ALWAYS call openApp for app-opening requests. ALWAYS call readNotifications when asked to read notifications."
    }

    private fun buildToolDeclarations(): JSONArray = JSONArray().apply {
        put(functionDeclaration("openWebsite", "Open a website URL.", JSONObject().apply { put("url", property("STRING", "Full URL.")); put("name", property("STRING", "Friendly name.")) }, JSONArray().put("url")))
        put(functionDeclaration("openApp", "Launch an installed Android app by name.", JSONObject().put("appName", property("STRING", "Visible app name.")), JSONArray().put("appName")))
        put(functionDeclaration("sendEmail", "Send an email using the configured SMTP account (Gmail App Password).", JSONObject().apply { put("to", property("STRING", "Recipient email address.")); put("subject", property("STRING", "Email subject.")); put("body", property("STRING", "Email body text.")) }, JSONArray().put("to").put("body")))
        put(functionDeclaration("readNotifications", "Read active notifications.", JSONObject(), JSONArray()))
        put(functionDeclaration("replyToNotification", "Reply to a notification.", JSONObject().apply { put("query", property("STRING", "Notification search text.")); put("replyText", property("STRING", "Reply text.")) }, JSONArray().put("query").put("replyText")))
        put(functionDeclaration("getDeviceInfo", "Read device telemetry.", JSONObject(), JSONArray()))
        put(functionDeclaration("getScreenInfo", "Read current screen accessibility structure.", JSONObject(), JSONArray()))
        put(functionDeclaration("savePersonalFact", "Save a personal memory.", JSONObject().put("fact", property("STRING", "Fact to remember.")), JSONArray().put("fact")))
        put(functionDeclaration("forgetPersonalFact", "Forget a personal memory.", JSONObject().put("fact", property("STRING", "Fact to forget.")), JSONArray().put("fact")))
    }

    private fun functionDeclaration(name: String, description: String, properties: JSONObject, required: JSONArray) =
        JSONObject().put("name", name).put("description", description).put("parameters", JSONObject().put("type", "object").put("properties", properties).put("required", required))

    private fun property(type: String, description: String) = JSONObject().put("type", type.lowercase()).put("description", description)

    private fun ensureInitialized() { check(initialized) { "ZoyaSessionManager is not initialized" } }
    private fun isConnected(): Boolean = client != null
    private fun startForegroundService() {
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, ZoyaForegroundService::class.java))
        }
    }
    private fun stopForegroundService() { runCatching { app.stopService(Intent(app, ZoyaForegroundService::class.java)) } }

    fun setLanguage(lang: ZoyaLanguage) {}
    fun startVisionSession() { connect() }
    fun sendVisionFrame(base64Jpeg: String) { if (base64Jpeg.isNotBlank() && isConnected()) client?.sendVideoFrame(base64Jpeg) }
    fun restoreIfNeeded() { if (prefs.getBoolean("active", false) && !isConnected()) connectInternal() }
    fun analyzeVisionFrame(jpegBytes: ByteArray, prompt: String, onComplete: ((String) -> Unit)? = null) {}
    fun clearMemories() { sessionScope.launch { repository.clearMemories() } }
    fun clearChatHistory() { sessionScope.launch { repository.clearChatMessages() } }
    fun dismissError() { _state.update { it.copy(error = null) } }
    fun onApiKeyUpdated(newKey: String) {}
    fun onSettingsUpdated() { if (isConnected()) { client?.disconnect(); connectInternal() } }
}
