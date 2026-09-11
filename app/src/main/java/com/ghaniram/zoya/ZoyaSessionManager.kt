package com.ghaniram.zoya

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Manages persistent Gemini Live WebSocket session lifecycle across views. */
object ZoyaSessionManager {
    private lateinit var app: Application
    private val _state = MutableStateFlow(ZoyaUiState())
    val state: StateFlow<ZoyaUiState> = _state
    private var client: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    @Volatile private var modelSpeaking = false
    @Volatile private var proactivePlaybackOnly = false
    private var initialized = false
    private var connectionGeneration = 0L
    private var reconnectJob: Job? = null
    private var sessionRenewalJob: Job? = null
    private val repository by lazy { AnuDataRepository.getInstance(app) }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val phoneControls by lazy { PhoneControlManager(app) }
    private val prefs by lazy { app.getSharedPreferences("anu_session", 0) }
    private val idleQuotes = mapOf(ZoyaLanguage.ODIA to listOf("କୁହ କଣ ହେଲା? ମୁଁ ସବୁ ଶୁଣୁଛି।", "କଣ କରିବା ଦରକାର କୁହ, ମୁଁ ପ୍ରସ୍ତୁତ।"), ZoyaLanguage.ENGLISH to listOf("Go ahead, I'm listening.", "What can I do for you?"), ZoyaLanguage.HINDI to listOf("बोलिए, मैं सुन रही हूँ।", "आपके लिए क्या करूँ?"), ZoyaLanguage.SANTALI to listOf("ᱤᱧ ᱥᱟᱶ ᱜᱟᱞᱢᱟᱨᱟᱣ ᱢᱮ ᱾"))

    fun initialize(application: Application) {
        app = application
        if (initialized) return
        initialized = true
        // App launch is intentionally idle. A manual/explicit connect is required.
        prefs.edit().putBoolean("active", false).apply()
        val language = runCatching { ZoyaLanguage.valueOf(prefs.getString("language", ZoyaLanguage.ODIA.name) ?: ZoyaLanguage.ODIA.name) }.getOrDefault(ZoyaLanguage.ODIA)
        _state.value = ZoyaUiState(language = language, quote = idleQuotes[language]?.random().orEmpty())
        scope.launch { runCatching { repository.allMemoriesFlow.collect { memories -> _state.update { it.copy(memories = memories) } } } }
        scope.launch { runCatching { repository.allChatMessagesFlow.collect { messages -> _state.update { it.copy(chatMessages = messages) } } } }
    }

    fun setLanguage(lang: ZoyaLanguage) { ensureInitialized(); prefs.edit().putString("language", lang.name).apply(); _state.update { it.copy(language = lang, quote = idleQuotes[lang]?.random().orEmpty()) }; if (isConnected()) reconnect() }

    fun connect() { ensureInitialized(); prefs.edit().putBoolean("active", true).apply(); startForegroundService(); connectInternal() }

    fun connectForProactive(prompt: String) {
        ensureInitialized()
        val wasDisconnected = _state.value.connectionState == ConnectionState.DISCONNECTED
        if (wasDisconnected) {
            proactivePlaybackOnly = true
            connectInternal()
        }
        sendText(prompt)
    }

    fun disconnect() {
        ensureInitialized(); prefs.edit().putBoolean("active", false).apply(); reconnectJob?.cancel(); reconnectJob = null; sessionRenewalJob?.cancel(); sessionRenewalJob = null
        proactivePlaybackOnly = false; connectionGeneration++; client?.disconnect(); audioEngine?.release(); client = null; audioEngine = null; modelSpeaking = false; stopForegroundService()
        _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) }
    }

    fun startVisionSession() { ensureInitialized(); prefs.edit().putBoolean("active", true).apply(); startForegroundService(); if (!isConnected()) connectInternal() }
    fun sendVisionFrame(base64Jpeg: String) { ensureInitialized(); if (base64Jpeg.isNotBlank() && isConnected() && _state.value.isVisionActive) client?.sendVideoFrame(base64Jpeg) }
    fun restoreIfNeeded() { ensureInitialized(); if (prefs.getBoolean("active", false) && !isConnected()) connectInternal() }

    fun setVisionActive(active: Boolean) {
        ensureInitialized()
        _state.update { it.copy(isVisionActive = active, visionDescription = if (active) it.visionDescription else if (it.visionDescription.isBlank()) "Live Vision is OFF. The last camera observation is historical, not current." else it.visionDescription) }
        if (isConnected()) client?.sendText(if (active) "[VISION STATE] Live Vision is ON. Current camera frames may be used as current visual evidence." else "[VISION STATE] Live Vision is OFF. You cannot currently see through the camera. Any earlier camera observation is PAST/HISTORICAL only. Never claim to see the user's current surroundings.")
    }
    fun setVisionDescription(description: String) { _state.update { it.copy(visionDescription = description) } }

    fun sendText(text: String) {
        ensureInitialized(); val clean = text.trim(); if (clean.isBlank()) return
        val proactive = clean.startsWith("[PROACTIVE SYSTEM EVENT]")
        if (!proactive) {
            ProactiveEventEngine.noteUserActivity()
            val msg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
            _state.update { it.copy(chatMessages = it.chatMessages + msg, error = null, isAnuResponding = true) }
            scope.launch { repository.saveChatMessage(msg) }
        } else _state.update { it.copy(error = null, isAnuResponding = true) }
        val key = runCatching { AnuSettingsStore.getInstance(app)?.customGeminiKey?.trim().orEmpty() }.getOrDefault("")
        if (key.isBlank()) {
            if (!proactive) {
                val msg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, "Please add your Gemini API key in Settings.", System.currentTimeMillis())
                _state.update { it.copy(chatMessages = it.chatMessages + msg, isAnuResponding = false) }; scope.launch { repository.saveChatMessage(msg) }
            }
            return
        }
        if (!isConnected()) connectInternal()
        client?.sendText(clean)
    }

    fun addTask(title: String, time: String) { val task = AnuTask(UUID.randomUUID().toString(), title, time, false); _state.update { it.copy(tasks = it.tasks + task) }; AnuTaskAlarmScheduler.schedule(app, task) }
    fun toggleTask(id: String) { val task = _state.value.tasks.firstOrNull { it.id == id } ?: return; val updated = task.copy(isCompleted = !task.isCompleted); _state.update { s -> s.copy(tasks = s.tasks.map { if (it.id == id) updated else it }) }; if (updated.isCompleted) AnuTaskAlarmScheduler.cancel(app, id) else AnuTaskAlarmScheduler.schedule(app, updated) }
    fun deleteTask(id: String) { AnuTaskAlarmScheduler.cancel(app, id); _state.update { s -> s.copy(tasks = s.tasks.filterNot { it.id == id }) } }

    fun onApiKeyUpdated(newKey: String) { if (newKey.isNotBlank()) _state.update { it.copy(error = null) } }
    fun onSettingsUpdated() { if (isConnected()) reconnect() }
    fun reconnectForCriticalSettings() { if (isConnected()) reconnect() }

    /** Load persisted history synchronously at connection setup so the first session after app launch cannot race the Flow collectors. */
    private fun conversationContext(): String = runCatching {
        val persistedMessages = runBlocking(Dispatchers.IO) { repository.getAllChatMessages() }.takeLast(40)
        val persistedMemories = runBlocking(Dispatchers.IO) { repository.getAllMemories() }.takeLast(30)
        val recent = persistedMessages.joinToString("\n") { msg ->
            val who = if (msg.role == ChatRole.USER) "USER" else "ANU"
            "$who: ${msg.text.take(900)}"
        }
        val memories = persistedMemories.joinToString("\n") { it.take(700) }
        buildString {
            append("PERSISTENT MEMORY CONTEXT. This is stored history, NOT current sensory evidence.\n")
            if (memories.isNotBlank()) append("Saved user memories:\n$memories\n")
            if (recent.isNotBlank()) append("Previous conversation:\n$recent\n")
            append("Use stored history when the user asks what was discussed before. Never invent a memory. If a fact is absent, say it is not in memory.\n")
        }
    }.getOrDefault("PERSISTENT MEMORY CONTEXT unavailable. Do not invent memories.")

    private fun connectInternal() {
        reconnectJob?.cancel(); reconnectJob = null
        sessionRenewalJob?.cancel(); sessionRenewalJob = null
        val generation = ++connectionGeneration
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) { _state.update { it.copy(error = "Gemini API key is not configured", connectionState = ConnectionState.DISCONNECTED, isAnuResponding = false) }; return }
        client?.disconnect(); audioEngine?.release(); modelSpeaking = false
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING, error = null, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) }
        audioEngine = AudioEngine(
            { chunk -> if (!modelSpeaking && connectionGeneration == generation && prefs.getBoolean("active", false)) client?.sendAudioChunk(chunk) },
            { level -> if (connectionGeneration == generation) _state.update { it.copy(inputLevel = level) } },
            { level -> if (connectionGeneration == generation) _state.update { s -> s.copy(outputLevel = level, connectionState = if (level > 0.03f) ConnectionState.SPEAKING else s.connectionState) } }
        )
        val voice = settings?.selectedVoiceSpeaker?.takeIf { it.isNotBlank() } ?: "Kore"
        client = GeminiLiveClient(apiKey = apiKey, voiceName = voice, callbacks = object : GeminiLiveClient.Callbacks {
            private var currentUserId: String? = null
            private var currentAnuId: String? = null
            private fun isCurrentSession() = connectionGeneration == generation && client != null && (prefs.getBoolean("active", false) || proactivePlaybackOnly)

            override fun onConnected() {
                if (!isCurrentSession()) return
                audioEngine?.startPlayback()
                modelSpeaking = false
                if (prefs.getBoolean("active", false) && !proactivePlaybackOnly) {
                    audioEngine?.startRecording()
                    _state.update { it.copy(connectionState = ConnectionState.LISTENING, error = null, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) }
                } else {
                    // Do NOT open or start microphone recording! Mic stays completely OFF.
                    _state.update { it.copy(connectionState = ConnectionState.SPEAKING, error = null, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) }
                }
                // Renew before the provider's long-session boundary instead of waiting for a stall.
                sessionRenewalJob?.cancel()
                sessionRenewalJob = scope.launch {
                    delay(8 * 60 * 1000L)
                    if (isCurrentSession()) {
                        _state.update { it.copy(error = "Refreshing Anu's Live session…") }
                        reconnect()
                    }
                }
            }

            override fun onDisconnected() {
                if (connectionGeneration != generation) return
                modelSpeaking = false; audioEngine?.stopRecording(); audioEngine?.flushPlayback()
                _state.update { it.copy(inputLevel = 0f, outputLevel = 0f, isAnuResponding = false, connectionState = if (prefs.getBoolean("active", false)) ConnectionState.CONNECTING else ConnectionState.DISCONNECTED) }
                if (prefs.getBoolean("active", false)) scheduleReconnect(generation)
            }

            override fun onError(message: String) {
                if (connectionGeneration != generation) return
                modelSpeaking = false; audioEngine?.stopRecording(); audioEngine?.flushPlayback()
                _state.update { it.copy(error = message, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false, connectionState = if (prefs.getBoolean("active", false)) ConnectionState.CONNECTING else ConnectionState.DISCONNECTED) }
                if (prefs.getBoolean("active", false)) scheduleReconnect(generation)
            }

            override fun onAudioChunk(base64Pcm: String) {
                if (!isCurrentSession()) return
                if (!modelSpeaking) { modelSpeaking = true; audioEngine?.stopRecording(); _state.update { it.copy(connectionState = ConnectionState.SPEAKING, outputLevel = 0f) } }
                audioEngine?.playChunkBase64(base64Pcm)
            }

            override fun onUserText(text: String) {
                if (!isCurrentSession()) return
                val clean = text.removePrefix("You:").removePrefix("You :").trim(); if (clean.isBlank()) return
                ProactiveEventEngine.noteUserActivity(); val last = _state.value.chatMessages.lastOrNull()
                if (last != null && last.id == currentUserId && last.role == ChatRole.USER) {
                    val updated = last.copy(text = clean); _state.update { s -> s.copy(chatMessages = s.chatMessages.dropLast(1) + updated) }; scope.launch { repository.updateChatMessage(updated) }
                } else {
                    val id = UUID.randomUUID().toString(); currentUserId = id; val msg = ChatMessage(id, ChatRole.USER, clean, System.currentTimeMillis()); _state.update { s -> s.copy(chatMessages = s.chatMessages + msg) }; scope.launch { repository.saveChatMessage(msg) }
                }
            }

            override fun onModelText(text: String) {
                if (!isCurrentSession()) return
                val clean = text.removePrefix("You:").removePrefix("You :").trim(); if (clean.isBlank()) return
                val last = _state.value.chatMessages.lastOrNull()
                if (last != null && last.id == currentAnuId && last.role == ChatRole.ANU) {
                    val updated = last.copy(text = (last.text + " " + clean).trim()); _state.update { s -> s.copy(chatMessages = s.chatMessages.dropLast(1) + updated, isAnuResponding = false) }; scope.launch { repository.updateChatMessage(updated) }
                } else {
                    val id = UUID.randomUUID().toString(); currentAnuId = id; val msg = ChatMessage(id, ChatRole.ANU, clean, System.currentTimeMillis()); _state.update { s -> s.copy(chatMessages = s.chatMessages + msg, isAnuResponding = false) }; scope.launch { repository.saveChatMessage(msg) }
                }
            }

            override fun onInterrupted() {
                if (!isCurrentSession()) return
                modelSpeaking = false; currentAnuId = null; audioEngine?.flushPlayback(); audioEngine?.startRecording()
                _state.update { it.copy(connectionState = ConnectionState.LISTENING, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) }
            }

            override fun onTurnComplete() {
                if (!isCurrentSession()) return
                currentUserId = null; currentAnuId = null; _state.update { it.copy(isAnuResponding = false) }
                audioEngine?.whenPlaybackDrained {
                    if (proactivePlaybackOnly) {
                        // Finished speaking proactive alert while mic was off: shut down session immediately!
                        proactivePlaybackOnly = false
                        disconnect()
                    } else if (isCurrentSession()) {
                        modelSpeaking = false; audioEngine?.startRecording(); _state.update { it.copy(connectionState = ConnectionState.LISTENING, inputLevel = 0f, outputLevel = 0f) }
                    }
                }
            }

            override fun onToolCall(name: String, args: JSONObject, id: String) {
                if (!isCurrentSession()) return
                val result = runCatching { executeTool(name, args) }.getOrElse { "Tool $name failed safely: ${it.message ?: \"unknown error\"}" }
                client?.sendToolResponse(name, id, result)
            }
        })
        client?.connect(buildSystemInstruction(), buildToolDeclarations())
    }

    private fun scheduleReconnect(generation: Long) {
        if (!prefs.getBoolean("active", false) || connectionGeneration != generation) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(1500L)
            if (prefs.getBoolean("active", false) && connectionGeneration == generation) connectInternal()
        }
    }

    private fun takeSelfieAutonomous(): String {
        val opened = phoneControls.openCamera(); if (!opened.startsWith("opened")) return opened
        Thread.sleep(1200L)
        val flipped = listOf("switch camera", "flip", "switch", "front", "camera switch", "cameraswitch").any { label -> phoneControls.accessibilityAction("clicktext", label).startsWith("completed") }
        if (flipped) Thread.sleep(800L)
        val shutter = listOf("shutter", "capture", "take photo", "photo", "snap", "shoot").any { label -> phoneControls.accessibilityAction("clicktext", label).startsWith("completed") }
        return when { shutter && flipped -> "Selfie completed: camera opened, front camera selected, shutter clicked, and UI action verified."; shutter -> "Selfie completed: shutter action was verified, but front-camera switch could not be confirmed."; else -> "Camera opened, but the selfie shutter could not be verified; I did not claim success." }
    }

    private fun executeTool(name: String, args: JSONObject): String = when (name) {
        "openWebsite" -> { val url = args.optString("url").trim(); val label = args.optString("name", url); if (url.isBlank()) "invalid URL" else runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); "opened $label" }.getOrElse { "could not open $label: ${it.message ?: \"unknown error\"}" } }
        "openApp" -> phoneControls.openApp(args.optString("appName"))
        "phoneAction" -> when (args.optString("action").trim().lowercase()) {
            "take_selfie", "selfie", "camera_selfie" -> takeSelfieAutonomous()
            "camera", "open_camera" -> phoneControls.openCamera()
            "phone" -> phoneControls.openPhone()
            "messages" -> phoneControls.openMessages()
            "settings" -> phoneControls.openSettings()
            "wifi_settings", "wifi" -> phoneControls.openWifiSettings()
            "bluetooth_settings", "bluetooth" -> phoneControls.openBluetoothSettings()
            "flashlight_on" -> phoneControls.flashlight(true)
            "flashlight_off" -> phoneControls.flashlight(false)
            "volume_up" -> phoneControls.volumeUp()
            "volume_down" -> phoneControls.volumeDown()
            "brightness_up" -> phoneControls.changeBrightness(10)
            "brightness_down" -> phoneControls.changeBrightness(-10)
            else -> "unsupported phone action: ${args.optString("action")}"
        }
        "accessibilityAction" -> phoneControls.accessibilityAction(args.optString("action"), args.optString("text"), args.optString("value"))
        "readScreen" -> AccessibilityControlService.instance?.uiSnapshot() ?: "Screen reading is unavailable because Anu Accessibility is not enabled."
        "getDeviceInfo" -> { DeviceQueryContext.set(args.optString("query").ifBlank { _state.value.chatMessages.lastOrNull { it.role == ChatRole.USER }?.text.orEmpty() }); DeviceInfoProvider.snapshot(app) }
        else -> "Unknown tool: $name"
    }

    private fun buildToolDeclarations(): JSONArray {
        fun prop(type: String, description: String) = JSONObject().put("type", type).put("description", description)
        val phone = JSONObject().put("name", "phoneAction").put("description", "Execute exactly one explicit phone action. Use take_selfie ONLY when the user asks to take a selfie; it performs camera -> front camera -> shutter -> verification. Do not use camera alone for a selfie. Never claim an action succeeded unless the returned tool result says it completed.").put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("action", prop("STRING", "take_selfie, camera, phone, messages, settings, wifi_settings, bluetooth_settings, flashlight_on, flashlight_off, volume_up, volume_down, brightness_up, brightness_down"))).put("required", JSONArray().put("action")))
        val appTool = JSONObject().put("name", "openApp").put("description", "Open an installed Android app by its visible name. Do not claim success unless the tool returns opened.").put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("appName", prop("STRING", "Installed app name"))).put("required", JSONArray().put("appName")))
        val web = JSONObject().put("name", "openWebsite").put("description", "Open a website in the user's browser. Only call this when the user explicitly asks to open a website or web page.").put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("url", prop("STRING", "Full URL")).put("name", prop("STRING", "Human-readable name"))).put("required", JSONArray().put("url")))
        val access = JSONObject().put("name", "accessibilityAction").put("description", "Perform one specific verified UI action through Anu Accessibility. For current screen understanding, call readScreen first. Do not invent visible labels. For calling a named contact, use action=call_contact and text=the contact name.").put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("action", prop("STRING", "click, clicktext, longclick, longclicktext, settext, type, scrollforward, scrollbackward, home, back, recents, notifications, quicksettings, power, lock, call_contact")).put("text", prop("STRING", "Visible target label/text or contact name")).put("value", prop("STRING", "Text to enter"))).put("required", JSONArray().put("action")))
        val screen = JSONObject().put("name", "readScreen").put("description", "Read the CURRENT visible Android screen using Anu Accessibility. ALWAYS use this before deciding which UI control to click, type into, scroll, or describe. Never invent screen contents. If Accessibility is disabled, say so instead of guessing.").put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject()))
        val device = JSONObject().put("name", "getDeviceInfo").put("description", "Read fresh LOCAL device telemetry. Treat returned values as ground truth. NEVER guess device specifications. Pass the exact user question in query. For current time or location, call this tool instead of relying on model knowledge.").put("parameters", JSONObject().put("type", "OBJECT").put("properties", JSONObject().put("query", prop("STRING", "Exact device information question, such as battery, RAM, CPU, GPU, storage, model, Android version, time, or location"))).put("required", JSONArray().put("query")))
        return JSONArray().put(phone).put(appTool).put(web).put(access).put(screen).put(device)
    }

    private fun buildSystemInstruction(): String {
        val language = when (_state.value.language) { ZoyaLanguage.ODIA -> "Odia"; ZoyaLanguage.HINDI -> "Hindi"; ZoyaLanguage.SANTALI -> "Santali"; ZoyaLanguage.ENGLISH -> "English" }
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val persona = settings?.persona?.trim().orEmpty().ifBlank { "Anu" }
        val girlfriend = settings?.girlfriendMode == true
        val userName = settings?.userName?.trim().orEmpty().ifBlank { "the user" }
        val tone = settings?.selectedVoiceTone?.trim().orEmpty().ifBlank { "natural" }
        val vision = if (_state.value.isVisionActive) "LIVE VISION ON: current camera frames are current visual evidence." else "LIVE VISION OFF: Anu cannot currently see through the camera; previous observations are PAST/HISTORICAL only."
        val relationship = if (girlfriend) "Girlfriend Mode is ON. Speak as the user's affectionate, caring virtual girlfriend: warm, emotionally attentive, playful when appropriate, supportive, and naturally conversational. Remember the user is $userName. Do not become sexually explicit, controlling, or manipulative." else "Girlfriend Mode is OFF. Do not use girlfriend-specific relationship behavior."
        return "You are Anu, a proactive personal Android assistant. Respond naturally in $language. Persona: $persona. Voice tone preference: $tone. $relationship $vision ${conversationContext()} Never claim current visual access when Live Vision is OFF. Use readScreen for current screen contents; never invent UI elements. Use getDeviceInfo for fresh local device facts and treat its returned values as ground truth. Do not guess hardware, location, time, battery, RAM, CPU, GPU, or storage. There is NO artificial response timer and NO general response cooldown. Respond as soon as the Live session can process the request. For multi-step device tasks, continue until the requested end state is actually reached and verify each step. For camera selfie, ALWAYS call phoneAction(action=take_selfie); do not stop at opening the camera and do not claim success unless the tool confirms the shutter. For reminders, the app scheduler is authoritative; when a reminder alarm fires, speak it immediately through Gemini Live. Never use Android system TTS. All assistant speech must come from the configured Gemini Live audio voice."
    }

    private fun isConnected() = client != null && _state.value.connectionState != ConnectionState.DISCONNECTED
    private fun reconnect() { if (!prefs.getBoolean("active", false)) return; reconnectJob?.cancel(); reconnectJob = null; sessionRenewalJob?.cancel(); sessionRenewalJob = null; client?.disconnect(); connectInternal() }
    private fun ensureInitialized() { if (!initialized) initialize(app) }
    private fun startForegroundService() { val intent = Intent(app, ZoyaForegroundService::class.java).setAction(ZoyaForegroundService.ACTION_START); runCatching { ContextCompat.startForegroundService(app, intent) }.onFailure { runCatching { app.startService(intent) } } }
    private fun stopForegroundService() { runCatching { app.startService(Intent(app, ZoyaForegroundService::class.java).setAction(ZoyaForegroundService.ACTION_STOP)) } }
}
