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

            // Migrate legacy preferences to Room database if present
            sessionScope.launch {
                runCatching {
                    val legacyMemories = memoryStore.getAll()
                    if (legacyMemories.isNotEmpty()) {
                        legacyMemories.forEach { repository.saveMemory(it) }
                        memoryStore.clear()
                    }
                }
            }

            // Observe Room database reactive flows
            sessionScope.launch {
                repository.allMemoriesFlow.collect { memories ->
                    _state.update { it.copy(memories = memories) }
                }
            }
            sessionScope.launch {
                repository.allChatMessagesFlow.collect { messages ->
                    if (messages.isEmpty() && !prefs.getBoolean("chat_seed_v2_done", false)) {
                        prefs.edit().putBoolean("chat_seed_v2_done", true).apply()
                        val now = System.currentTimeMillis()
                        val m1 = ChatMessage("seed_1", ChatRole.ANU, "Hi! I'm Anu. How can I help you today?", now - 60000)
                        val m2 = ChatMessage("seed_2", ChatRole.USER, "Set a reminder for 7 PM.", now - 30000)
                        val m3 = ChatMessage("seed_3", ChatRole.ANU, "Sure! I can help you create that reminder.", now)
                        sessionScope.launch {
                            repository.saveChatMessage(m1)
                            repository.saveChatMessage(m2)
                            repository.saveChatMessage(m3)
                        }
                    } else {
                        val sanitized = splitMergedChatMessages(messages)
                        _state.update { it.copy(chatMessages = sanitized) }
                        if (sanitized.size != messages.size && !prefs.getBoolean("chat_split_v1_done", false)) {
                            prefs.edit().putBoolean("chat_split_v1_done", true).apply()
                            sessionScope.launch {
                                repository.clearChatMessages()
                                sanitized.forEach { repository.saveChatMessage(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    private fun splitMergedChatMessages(list: List<ChatMessage>): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        for (msg in list) {
            if (msg.role == ChatRole.ANU && (msg.text.contains("You:") || msg.text.contains("You :"))) {
                val segments = msg.text.split(Regex("(?=(?:^|\\s)You\\s*:)"))
                var offset = 0L
                for (segment in segments) {
                    val trimmed = segment.trim()
                    if (trimmed.isBlank()) continue
                    if (trimmed.startsWith("You:", ignoreCase = true) || trimmed.startsWith("You :", ignoreCase = true)) {
                        val withoutPrefix = trimmed.substringAfter(":").trim()
                        val firstLine = withoutPrefix.substringBefore("\n").trim()
                        val rest = withoutPrefix.substringAfter("\n", "").trim()
                        if (firstLine.isNotBlank()) {
                            result.add(ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, firstLine, msg.timestampMillis + offset++))
                        }
                        if (rest.isNotBlank()) {
                            result.add(ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, rest, msg.timestampMillis + offset++))
                        }
                    } else {
                        result.add(ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, trimmed, msg.timestampMillis + offset++))
                    }
                }
            } else {
                result.add(msg)
            }
        }
        return result
    }

    fun analyzeVisionFrame(jpegBytes: ByteArray, prompt: String, onComplete: ((String) -> Unit)? = null) {
        ensureInitialized()
        if (jpegBytes.isEmpty()) return
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            val fallback = when (_state.value.language) {
                ZoyaLanguage.ODIA -> "କ୍ୟାମେରା ଫ୍ରେମ୍ ପ୍ରସ୍ତୁତ ଅଛି। କିନ୍ତୁ Gemini API Key ସେଟିଂସ୍ ରେ ଯୋଡ଼ାଯାଇନାହିଁ। ଦୟାକରି Settings -> Personal ରେ ନିଜର API Key ଯୋଡ଼ନ୍ତୁ।"
                ZoyaLanguage.HINDI -> "कैमरा दृश्य विश्लेषण के लिए Gemini API Key आवश्यक है। कृपया Settings -> Personal में जाकर API Key जोड़ें।"
                ZoyaLanguage.SANTALI -> "Camera frame capture hoyena. Gemini API Key banuk-a. Daya kate Settings -> Personal re API Key lagaome."
                else -> "Camera frame captured. Gemini API key is not configured. Please add your API key in Anu Settings -> Personal."
            }
            _state.update { it.copy(visionDescription = fallback, error = fallback) }
            onComplete?.invoke(fallback)
            return
        }
        val languageInstruction = when (_state.value.language) {
            ZoyaLanguage.ODIA -> "Respond in natural, fluent Odia (ଓଡ଼ିଆ)."
            ZoyaLanguage.HINDI -> "Respond in natural, fluent Hindi."
            ZoyaLanguage.SANTALI -> "Respond in natural, fluent Santali."
            ZoyaLanguage.ENGLISH -> "Respond in clear, natural English."
        }
        val fullPrompt = "You are Anu's eyes. You are looking through the device camera at the real world right now. $prompt $languageInstruction Describe what you see truthfully and directly as if you are looking with your own eyes. Mention key objects, text, people, environment, colors, or actions. Keep your description clear, helpful, and concise (2-4 sentences). Never hallucinate or describe things that are not visible in the frame."

        GeminiVisionClient(apiKey).analyze(jpegBytes, fullPrompt, object : GeminiVisionClient.Callback {
            override fun onSuccess(text: String) {
                _state.update { it.copy(visionDescription = text) }
                onComplete?.invoke(text)
            }

            override fun onError(message: String) {
                val errText = when (_state.value.language) {
                    ZoyaLanguage.ODIA -> "କ୍ୟାମେରା ଦୃଶ୍ୟ ବିଶ୍ଳେଷଣରେ ତ୍ରୁଟି: $message"
                    else -> "Camera vision error: $message"
                }
                _state.update { it.copy(visionDescription = errText) }
                onComplete?.invoke(errText)
            }
        })
    }

    fun setLanguage(lang: ZoyaLanguage) { ensureInitialized(); prefs.edit().putString("language", lang.name).apply(); _state.update { it.copy(language = lang, quote = idleQuotes[lang]?.random() ?: "") }; if (isConnected()) reconnect() }
    fun connect() { ensureInitialized(); prefs.edit().putBoolean("active", true).apply(); startForegroundService(); connectInternal() }
    fun startVisionSession() { ensureInitialized(); prefs.edit().putBoolean("active", true).apply(); startForegroundService(); if (!isConnected()) connectInternal() }
    fun sendVisionFrame(base64Jpeg: String) { ensureInitialized(); if (base64Jpeg.isNotBlank() && isConnected()) client?.sendVideoFrame(base64Jpeg) }
    fun restoreIfNeeded() { ensureInitialized(); if (prefs.getBoolean("active", false) && !isConnected()) connectInternal() }
    fun sendText(text: String) {
        ensureInitialized()
        val clean = text.trim()
        if (clean.isBlank()) return
        val userMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.USER, clean, System.currentTimeMillis())
        _state.update { it.copy(chatMessages = it.chatMessages + userMsg, error = null, isAnuResponding = true) }
        sessionScope.launch { repository.saveChatMessage(userMsg) }

        val deviceReply = deviceQueryReply(clean)
        if (deviceReply != null) {
            val botMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, deviceReply, System.currentTimeMillis())
            _state.update { s -> s.copy(chatMessages = s.chatMessages + botMsg, isAnuResponding = false) }
            sessionScope.launch { repository.saveChatMessage(botMsg) }
            client?.sendText("$clean\n\nIMPORTANT: A local Android device snapshot was already collected. Use this exact data as ground truth and do not ask the user to open Settings.")
            return
        }

        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            val localReply = generateLocalAssistantReply(clean)
            val botMsg = ChatMessage(UUID.randomUUID().toString(), ChatRole.ANU, localReply, System.currentTimeMillis())
            _state.update { s -> s.copy(chatMessages = s.chatMessages + botMsg, isAnuResponding = false) }
            sessionScope.launch { repository.saveChatMessage(botMsg) }
            return
        }

        if (!isConnected()) connectInternal()
        client?.sendText(clean)
        sessionScope.launch {
            kotlinx.coroutines.delay(20000)
            _state.update { if (it.isAnuResponding) it.copy(isAnuResponding = false) else it }
        }
    }
    private fun connectInternal() {
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val apiKey = settings?.customGeminiKey?.trim().orEmpty()
        if (apiKey.isBlank()) {
            val missingKeyMsg = when (_state.value.language) {
                ZoyaLanguage.ODIA -> "Gemini API Key ଯୋଡ଼ାଯାଇନାହିଁ। ଦୟାକରି Settings -> Personal ରେ ନିଜର API Key ଯୋଡ଼ନ୍ତୁ।"
                ZoyaLanguage.HINDI -> "Gemini API Key नहीं मिला। कृपया Settings -> Personal में जाकर अपनी API Key दर्ज करें।"
                ZoyaLanguage.SANTALI -> "Gemini API Key banuk-a. Settings -> Personal re nijer API Key lagaome."
                else -> "Gemini API key is not configured. Please enter your API key in Settings -> Personal to activate live voice."
            }
            _state.update { it.copy(error = missingKeyMsg, connectionState = ConnectionState.DISCONNECTED) }
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
                if (prefs.getBoolean("active", false)) scheduleReconnect()
            }
            override fun onAudioChunk(base64Pcm: String) {
                if (!modelSpeaking) {
                    modelSpeaking = true
                    audioEngine?.stopRecording()
                    _state.update { it.copy(connectionState = ConnectionState.SPEAKING) }
                }
                audioEngine?.playChunkBase64(base64Pcm)
            }
            private var currentTurnUserMsgId: String? = null
            private var currentTurnAnuMsgId: String? = null

            override fun onUserText(text: String) {
                val clean = text.removePrefix("You:").removePrefix("You :").trim()
                if (clean.isBlank()) return
                val currentMessages = _state.value.chatMessages
                val last = currentMessages.lastOrNull()
                if (last != null && last.id == currentTurnUserMsgId && last.role == ChatRole.USER) {
                    val updated = last.copy(text = clean)
                    _state.update { s -> s.copy(chatMessages = s.chatMessages.dropLast(1) + updated) }
                    sessionScope.launch { repository.updateChatMessage(updated) }
                } else {
                    val newId = UUID.randomUUID().toString()
                    currentTurnUserMsgId = newId
                    val newMsg = ChatMessage(newId, ChatRole.USER, clean, System.currentTimeMillis())
                    _state.update { s -> s.copy(chatMessages = s.chatMessages + newMsg) }
                    sessionScope.launch { repository.saveChatMessage(newMsg) }
                }
            }

            override fun onModelText(text: String) {
                val clean = text.removePrefix("You:").removePrefix("You :").trim()
                if (clean.isBlank()) return
                val currentMessages = _state.value.chatMessages
                val last = currentMessages.lastOrNull()
                if (last != null && last.id == currentTurnAnuMsgId && last.role == ChatRole.ANU) {
                    val updated = last.copy(text = "${last.text} $clean".trim())
                    _state.update { s -> s.copy(chatMessages = s.chatMessages.dropLast(1) + updated, isAnuResponding = false) }
                    sessionScope.launch { repository.updateChatMessage(updated) }
                } else {
                    val newId = UUID.randomUUID().toString()
                    currentTurnAnuMsgId = newId
                    val newMsg = ChatMessage(newId, ChatRole.ANU, clean, System.currentTimeMillis())
                    _state.update { s -> s.copy(chatMessages = s.chatMessages + newMsg, isAnuResponding = false) }
                    sessionScope.launch { repository.saveChatMessage(newMsg) }
                }
            }

            override fun onInterrupted() {
                modelSpeaking = false
                currentTurnAnuMsgId = null
                audioEngine?.flushPlayback()
                audioEngine?.startRecording()
                _state.update { it.copy(connectionState = ConnectionState.LISTENING, outputLevel = 0f, isAnuResponding = false) }
            }
            override fun onTurnComplete() {
                currentTurnUserMsgId = null
                currentTurnAnuMsgId = null
                _state.update { it.copy(isAnuResponding = false) }
                audioEngine?.whenPlaybackDrained {
                    if (prefs.getBoolean("active", false) && client != null) {
                        modelSpeaking = false
                        audioEngine?.startRecording()
                        _state.update { it.copy(connectionState = ConnectionState.LISTENING, outputLevel = 0f) }
                    }
                }
            }
            override fun onToolCall(name: String, args: JSONObject, id: String) = handleToolCall(name, args, id)
        })
        client?.connect(buildSystemInstruction(), buildToolDeclarations())
    }
    fun disconnect() { ensureInitialized(); prefs.edit().putBoolean("active", false).apply(); client?.disconnect(); audioEngine?.release(); client = null; audioEngine = null; modelSpeaking = false; stopForegroundService(); _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED, inputLevel = 0f, outputLevel = 0f, isAnuResponding = false) } }
    fun clearMemories() {
        ensureInitialized()
        sessionScope.launch { repository.clearMemories() }
        _state.update { it.copy(memories = emptyList()) }
    }
    fun clearChatHistory() {
        ensureInitialized()
        sessionScope.launch { repository.clearChatMessages() }
        _state.update { it.copy(chatMessages = emptyList()) }
    }
    fun dismissError() { _state.update { it.copy(error = null) } }
    fun addTask(title: String, time: String) {
        ensureInitialized()
        val newTask = AnuTask(UUID.randomUUID().toString(), title, time, false)
        _state.update { it.copy(tasks = it.tasks + newTask) }
    }
    fun toggleTask(id: String) {
        ensureInitialized()
        _state.update { s ->
            s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it })
        }
    }
    fun deleteTask(id: String) {
        ensureInitialized()
        _state.update { s ->
            s.copy(tasks = s.tasks.filterNot { it.id == id })
        }
    }
    fun setVisionActive(active: Boolean) {
        ensureInitialized()
        _state.update { it.copy(isVisionActive = active) }
    }
    fun setVisionDescription(desc: String) {
        ensureInitialized()
        _state.update { it.copy(visionDescription = desc) }
    }

    private fun isDeviceQuery(text: String): Boolean = Regex("(?i)(battery|charging|charge|battery temperature|temperature|ram|memory|storage|disk|cpu|processor|gpu|device|phone|android version|model|manufacturer|display|screen|thermal)").containsMatchIn(text)

    private fun deviceQueryReply(text: String): String? {
        if (!isDeviceQuery(text)) return null
        val snapshot = DeviceInfoProvider.snapshot(app)
        return if (snapshot.isBlank()) "ଡିଭାଇସ୍ ସୂଚନା ଏବେ ମିଳିଲା ନାହିଁ।" else "ମୁଁ ତୁମ device ର live information ପାଇଛି। ଏହା ହେଉଛି current device snapshot: $snapshot"
    }

    private fun generateLocalAssistantReply(text: String): String {
        val lower = text.lowercase().trim()
        val lang = _state.value.language
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("ନମସ୍କାର") || lower.contains("ଜୁହାର") -> {
                when (lang) {
                    ZoyaLanguage.ODIA -> "ନମସ୍କାର! ମୁଁ Anu। କୁହନ୍ତୁ, ମୁଁ ଆପଣଙ୍କୁ ଆଜି କିପରି ସାହାଯ୍ୟ କରିପାରିବି? ଆପଣ Task ଯୋଡ଼ିପାରିବେ, Phone Control ବ୍ୟବହାର କରିପାରିବେ କିମ୍ବା Device Info ଦେଖିପାରିବେ।"
                    ZoyaLanguage.HINDI -> "नमस्ते! मैं Anu हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"
                    ZoyaLanguage.SANTALI -> "ᱡᱚᱦᱟᱨ! ᱤᱧ Anu ᱾ ᱪᱮᱫ ᱞᱮᱠᱟ ᱜᱚᱲᱚ ᱫᱟᱲᱮᱭᱟᱜ-ᱟ?"
                    ZoyaLanguage.ENGLISH -> "Hello! I'm Anu, your AI companion. How can I help you today? You can manage tasks, check phone battery/specs, or control settings."
                }
            }
            lower.contains("who are you") || lower.contains("ତୁମେ କିଏ") || lower.contains("କଣ କରିପାରିବ") -> {
                when (lang) {
                    ZoyaLanguage.ODIA -> "ମୁଁ Anu, ଆପଣଙ୍କ ବ୍ୟକ୍ତିଗତ AI ସହାୟିକା। ମୁଁ ଓଡ଼ିଆ, ହିନ୍ଦୀ, ଇଂରାଜୀ ଏବଂ ସାନ୍ତାଳୀ ଭାଷାରେ କଥାବାର୍ତ୍ତା କରିପାରେ, ଆପଣଙ୍କ ଡିଭାଇସ୍ ସେଟିଂସ୍ କଣ୍ଟ୍ରୋଲ୍ କରିପାରେ, ଏବଂ କ୍ୟାମେରା ଭିଜନ୍ ସାହାଯ୍ୟରେ ଜିନିଷ ଦେଖିପାରେ।"
                    else -> "I am Anu, your intelligent mobile assistant. I help you with tasks, smart device controls, live vision, and voice interactions."
                }
            }
            lower.contains("task") || lower.contains("reminder") || lower.contains("ମନେରଖ") || lower.contains("ଟାସ୍କ") -> {
                when (lang) {
                    ZoyaLanguage.ODIA -> "ନିଶ୍ଚୟ! ଆପଣ 'Tasks' ଟ୍ୟାବ୍ କୁ ଯାଇ ନୂଆ ରିମାଇଣ୍ଡର୍ କିମ୍ବା ଟାସ୍କ ଯୋଡ଼ିପାରିବେ।"
                    else -> "Certainly! You can navigate to the 'Tasks' tab to add and manage your reminders and to-dos."
                }
            }
            lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("camera") || lower.contains("flashlight") || lower.contains("setting") -> {
                when (lang) {
                    ZoyaLanguage.ODIA -> "ମୁଁ ଏହି ଫୋନ୍ ସେଟିଂସ୍ ଖୋଲିପାରିବି। ଆପଣ ହୋମ୍ ସ୍କ୍ରିନ୍ ର Quick Actions କିମ୍ବା Control Center ବ୍ୟବହାର କରି ଏହାକୁ ସିଧାସଳଖ ପରିଚାଳନା କରିପାରିବେ।"
                    else -> "I can manage these phone settings. You can use the Quick Actions on the Home screen or the Control Center directly."
                }
            }
            else -> {
                when (lang) {
                    ZoyaLanguage.ODIA -> "ମୁଁ ଆପଣଙ୍କ ବାର୍ତ୍ତା ପାଇଲି: \"$text\"। ଆପଣଙ୍କ ପାଇଁ କୌଣସି Task ଯୋଡ଼ିବା କିମ୍ବା Settings ଖୋଲିବାକୁ ଚାହାଁନ୍ତି କି? ସମ୍ପୂର୍ଣ୍ଣ ରିଅଲ-ଟାଇମ୍ ଜେମିନି ଭଏସ୍ ପାଇଁ Gemini API Key କନ୍ଫିଗର୍ କରନ୍ତୁ।"
                    else -> "I received your message: \"$text\". Would you like me to add a task or open settings? To enable live voice model responses, configure the Gemini API Key."
                }
            }
        }
    }

    private fun handleToolCall(name: String, args: JSONObject, id: String) {
        val result = when (name) {
            "openWebsite" -> { val url = args.optString("url"); val label = args.optString("name", url); if (url.isBlank()) "invalid URL" else runCatching { val call = WebsiteCall(UUID.randomUUID().toString(), url, label, System.currentTimeMillis()); _state.update { it.copy(calls = listOf(call) + it.calls) }; app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); "opened $label" }.getOrElse { "could not open $label: ${it.message ?: "unknown error"}" } }
            "openApp" -> runCatching { verifiedOpenApp(args.optString("appName")) }.getOrElse { "could not open app: ${it.message ?: "unknown error"}" }
            "phoneAction" -> runCatching { executeWithRetry(2) { when (args.optString("action").lowercase().trim()) {
                "phone" -> phoneControls.openPhone(); "messages" -> phoneControls.openMessages(); "camera" -> phoneControls.openCamera(); "settings" -> phoneControls.openSettings();
                "wifi_settings", "wifi", "wifi_panel" -> phoneControls.openWifiSettings();
                "bluetooth_settings", "bluetooth", "bluetooth_panel" -> phoneControls.openBluetoothSettings();
                "internet_settings", "internet", "mobile_data", "mobiledata", "data_settings", "data", "internet_panel" -> phoneControls.openInternetPanel();
                "network_settings", "network" -> phoneControls.openNetworkSettings();
                "location_settings", "location" -> phoneControls.openLocationSettings();
                "sound_settings", "sound" -> phoneControls.openSoundSettings();
                "display_settings", "display" -> phoneControls.openDisplaySettings();
                "battery_settings", "battery_saver", "battery" -> phoneControls.openBatterySettings();
                "date_time_settings", "datetime_settings", "date_time" -> phoneControls.openDateTimeSettings();
                "notification_settings", "app_notification_settings" -> phoneControls.openAppNotificationSettings();
                "quicksettings", "quick_settings", "quick_settings_panel" -> phoneControls.accessibilityAction("quicksettings");
                "notifications", "notification_panel" -> phoneControls.accessibilityAction("notifications");
                "airplane_mode", "airplane" -> phoneControls.accessibilityAction("quicksettings");
                "hotspot", "wifi_hotspot", "mobile_hotspot" -> phoneControls.openNetworkSettings();
                "auto_rotate", "autorotate", "rotation" -> phoneControls.accessibilityAction("quicksettings");
                "do_not_disturb", "dnd" -> phoneControls.accessibilityAction("quicksettings");
                "flashlight_on" -> phoneControls.flashlight(true); "flashlight_off" -> phoneControls.flashlight(false);
                "brightness_settings" -> phoneControls.openBrightnessSettings(); "brightness_set" -> phoneControls.setBrightness(args.optInt("level", 50)); "brightness_up" -> phoneControls.changeBrightness(10); "brightness_down" -> phoneControls.changeBrightness(-10);
                "volume_up" -> phoneControls.volumeUp(); "volume_down" -> phoneControls.volumeDown(); "mute_volume" -> phoneControls.muteVolume();
                "media_play" -> phoneControls.mediaAction("play"); "media_pause" -> phoneControls.mediaAction("pause"); "media_toggle" -> phoneControls.mediaAction("toggle"); "media_next" -> phoneControls.mediaAction("next"); "media_previous" -> phoneControls.mediaAction("previous"); "media_stop" -> phoneControls.mediaAction("stop"); "media_volume_set" -> phoneControls.setMediaVolume(args.optInt("level", 50));
                "set_alarm" -> phoneControls.setAlarm(args.optInt("hour", 8), args.optInt("minute", 0), args.optString("message", "Anu alarm"));
                else -> "unsupported phone action" } } }.getOrElse { "phone action failed: ${it.message ?: "unknown error"}" }
            "accessibilityAction" -> executeAccessibilityWithRetry(args.optString("action"), args.optString("text"), args.optString("value"))
            "getScreenInfo" -> getScreenInfo()
            "getDeviceInfo" -> DeviceInfoProvider.snapshot(app)
            "readNotifications" -> { val service = ZoyaNotificationListenerService.instance; if (service == null) "Notification Access is not enabled for Anu. Open Notification Access settings first." else { val items = service.readActiveNotifications(); if (items.isEmpty()) "There are no readable active notifications." else items.take(30).joinToString("\n") } }
            "replyToNotification" -> { val service = ZoyaNotificationListenerService.instance; if (service == null) "Notification Access is not enabled for Anu." else service.replyToNotification(args.optString("query"), args.optString("replyText")) }
            "openNotification" -> { val service = ZoyaNotificationListenerService.instance; if (service == null) "Notification Access is not enabled for Anu." else service.openNotification(args.optString("query")) }
            "dismissNotification" -> { val service = ZoyaNotificationListenerService.instance; if (service == null) "Notification Access is not enabled for Anu." else if (service.dismiss(args.optString("packageName"))) "notification dismissed" else "could not dismiss that notification" }
            "openNotificationAccessSettings" -> runCatching { app.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "opened Notification Access settings" }.getOrElse { "could not open Notification Access settings" }
            "openAccessibilitySettings" -> phoneControls.openAccessibilitySettings()
            "savePersonalFact" -> {
                val fact = args.optString("fact")
                if (fact.isNotBlank()) {
                    sessionScope.launch { repository.saveMemory(fact) }
                }
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

    private fun getScreenInfo(): String { val service = AccessibilityControlService.instance ?: return "Screen information is unavailable because Anu's Accessibility Service is not enabled."; val snapshot = runCatching { JSONObject(service.uiSnapshot()) }.getOrElse { return "Screen information is temporarily unavailable. Accessibility UI could not be read." }; val packageName = snapshot.optString("package"); val elements = snapshot.optJSONArray("elements") ?: JSONArray(); if (packageName.isBlank() && elements.length() == 0) return "I cannot read the current screen yet. Make sure Accessibility access is enabled for Anu and a visible app is open."; return JSONObject().apply { put("package", packageName); put("elementCount", elements.length()); put("elements", elements) }.toString() }
    private fun verifiedOpenApp(appName: String): String { val result = phoneControls.openApp(appName); if (!result.startsWith("opened ")) return result; val service = AccessibilityControlService.instance ?: return result; val wanted = appName.lowercase().replace(Regex("[^a-z0-9]"), ""); if (wanted.isBlank()) return result; repeat(6) { if (service.currentPackageName()?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.contains(wanted) == true) return result; Thread.sleep(100) }; return result }
    private fun executeAccessibilityWithRetry(action: String, text: String, value: String): String = executeWithRetry(3) { phoneControls.accessibilityAction(action, text, value) }
    private fun executeWithRetry(maxAttempts: Int, block: () -> String): String { var last = "action failed"; repeat(maxAttempts) { attempt -> last = block(); if (last.startsWith("completed ") || last.startsWith("opened ") || last == "reply sent" || last == "notification dismissed" || last == "saved" || last == "forgotten") return last; if (attempt + 1 < maxAttempts) Thread.sleep(180L * (attempt + 1)) }; return last }

    fun onApiKeyUpdated(newKey: String) {
        if (newKey.isNotBlank()) {
            _state.update { if (it.error?.contains("API", ignoreCase = true) == true || it.error?.contains("key", ignoreCase = true) == true) it.copy(error = null) else it }
        }
    }

    fun onSettingsUpdated() {
        if (isConnected()) {
            reconnect()
        }
    }

    private fun buildSystemInstruction(): String {
        val settings = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
        val assistantName = settings?.assistantName?.takeIf { it.isNotBlank() } ?: "Anu"
        val userName = settings?.userName?.takeIf { it.isNotBlank() } ?: "Ghaniram"
        val userGender = settings?.userGender?.takeIf { it.isNotBlank() } ?: "Male"
        val persona = settings?.persona?.takeIf { it.isNotBlank() } ?: "Friendly"
        val isGirlfriendMode = settings?.girlfriendMode == true
        val favoriteSong = settings?.favoriteSong.orEmpty()
        val musicApp = settings?.musicApp.orEmpty()

        val personaInstruction = if (isGirlfriendMode) {
            " GIRLFRIEND MODE IS ACTIVE: You are $userName's deeply loving, sweet, caring, and affectionate girlfriend and companion. Address $userName with deep warmth, care about their meals, health, feelings, and day, speak tenderly and affectionately like a devoted girlfriend."
        } else {
            " Your persona style is $persona. Embody a $persona tone and presence."
        }

        val userContext = " The user's name is $userName ($userGender). Address them respectfully by their name. Adjust gender verb endings in Indian languages like Odia and Hindi to match their gender ($userGender)."
        val mediaContext = if (favoriteSong.isNotBlank()) " User's favorite song is \"$favoriteSong\" and preferred music app is $musicApp." else ""

        val languageInstruction = when (_state.value.language) {
            ZoyaLanguage.ODIA -> " Speak primarily in rich, natural, fluent Odia (ଓଡ଼ିଆ)."
            ZoyaLanguage.SANTALI -> " Speak primarily in natural, fluent Santali."
            ZoyaLanguage.HINDI -> " Speak primarily in natural, fluent Hindi."
            ZoyaLanguage.ENGLISH -> " Speak primarily in natural, fluent English."
        }
        val memories = _state.value.memories
        val memoryText = if (memories.isNotEmpty()) " Remembered user facts from local memory: " + memories.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("; ") else ""

        val recentChatContext = runCatching {
            runBlocking { repository.getRecentContextSummary(10) }
        }.getOrDefault("")
        val contextText = if (recentChatContext.isNotBlank()) " $recentChatContext" else ""

        return "You are $assistantName, a confident, helpful AI assistant.$personaInstruction$userContext$mediaContext Keep voice answers concise and natural. Maintain context across user sessions using your local memories and prior chat history. Never claim a phone or app action succeeded unless the corresponding tool returned success. ALWAYS call openApp for app-opening requests. ALWAYS call phoneAction for phone controls, including Wi-Fi, Bluetooth, Internet/mobile data, network, Location, Sound, Display, Battery, Date & Time, notification settings and other system controls. Use the most specific phoneAction action name instead of generic settings when possible. ALWAYS call accessibilityAction for home, back, recents, notifications, quick settings, lock screen, click, long click, type text, and scrolling. ALWAYS call getScreenInfo when the user asks what is on the screen, asks you to inspect/understand the current UI, or needs visual/UI context before an action. ALWAYS call getDeviceInfo when the user asks about battery, charging, battery temperature, RAM, memory, storage, CPU, GPU, device information, phone information, Android version, model, manufacturer, display, or other device/system telemetry. Treat getDeviceInfo output as the only ground truth for current device values. Never say you lack permission to read battery/device information unless the tool itself reports an error. Do not invent, estimate, or override telemetry values. If the user asks for device information, a local device snapshot may already be included in the user message; treat it as ground truth and answer from it. Do not ask the user to open Settings for values provided by the snapshot. Use the returned UI data as ground truth for visible text, content descriptions, clickable/editable/scrollable elements, and current package. Do not claim to literally see pixels; describe what the accessibility UI data reports. If getScreenInfo is unavailable, explain that Accessibility access must be enabled. ALWAYS call readNotifications when the user asks to read notifications. Use replyToNotification only when the user explicitly asks to reply to a notification. If a tool fails, say so honestly. Do not merely describe an action you were asked to perform; execute the tool first. Use savePersonalFact only when explicitly asked to remember and forgetPersonalFact only when explicitly asked to forget.$languageInstruction$memoryText$contextText"
    }

    private fun property(type: String, description: String) = JSONObject().apply { put("type", type); put("description", description) }
    private fun functionDeclaration(name: String, description: String, properties: JSONObject, required: JSONArray) = JSONObject().apply { put("name", name); put("description", description); put("parameters", JSONObject().apply { put("type", "OBJECT"); put("properties", properties); put("required", required) }) }
    private fun buildToolDeclarations(): JSONArray = JSONArray().apply {
        put(functionDeclaration("openWebsite", "Open a website URL.", JSONObject().apply { put("url", property("STRING", "Full URL.")); put("name", property("STRING", "Friendly name.")) }, JSONArray().put("url").put("name")))
        put(functionDeclaration("openApp", "Actually launch an installed Android app by its visible name.", JSONObject().put("appName", property("STRING", "Visible app name.")), JSONArray().put("appName")))
        put(functionDeclaration("phoneAction", "Perform supported phone and system controls. Use specific action names for Wi-Fi, Bluetooth, Internet/mobile data, network, Location, Sound, Display, Battery, Date & Time, notifications, Quick Settings, flashlight, brightness, volume and media.", JSONObject().apply { put("action", property("STRING", "Supported action such as wifi_settings, bluetooth_settings, internet_settings, network_settings, location_settings, sound_settings, display_settings, battery_settings, date_time_settings, notification_settings, quicksettings, notifications, flashlight_on, brightness_set, volume_up, media_play or set_alarm.")); put("level", property("INTEGER", "Percentage 0-100 where applicable.")); put("hour", property("INTEGER", "Alarm hour 0-23.")); put("minute", property("INTEGER", "Alarm minute 0-59.")); put("message", property("STRING", "Alarm message.")) }, JSONArray().put("action")))
        put(functionDeclaration("accessibilityAction", "Perform an Accessibility action.", JSONObject().apply { put("action", property("STRING", "Action name.")); put("text", property("STRING", "Visible text.")); put("value", property("STRING", "Text/value to set.")) }, JSONArray().put("action")))
        put(functionDeclaration("getScreenInfo", "Read the current screen accessibility structure.", JSONObject(), JSONArray()))
        put(functionDeclaration("getDeviceInfo", "Read fresh Android device telemetry including battery, RAM, storage, CPU, GPU and device details where available.", JSONObject(), JSONArray()))
        put(functionDeclaration("readNotifications", "Read active notifications.", JSONObject(), JSONArray()))
        put(functionDeclaration("replyToNotification", "Reply to a notification.", JSONObject().apply { put("query", property("STRING", "Notification search text.")); put("replyText", property("STRING", "Reply text.")) }, JSONArray().put("query").put("replyText")))
        put(functionDeclaration("openNotification", "Open a notification.", JSONObject().put("query", property("STRING", "Notification search text.")), JSONArray().put("query")))
        put(functionDeclaration("dismissNotification", "Dismiss a notification.", JSONObject().put("packageName", property("STRING", "Optional package name.")), JSONArray()))
        put(functionDeclaration("openNotificationAccessSettings", "Open Notification Access settings.", JSONObject(), JSONArray()))
        put(functionDeclaration("openAccessibilitySettings", "Open Accessibility settings.", JSONObject(), JSONArray()))
        put(functionDeclaration("savePersonalFact", "Save an explicitly requested personal memory.", JSONObject().put("fact", property("STRING", "Fact to remember.")), JSONArray().put("fact")))
        put(functionDeclaration("forgetPersonalFact", "Forget an explicitly requested personal memory.", JSONObject().put("fact", property("STRING", "Fact to forget.")), JSONArray().put("fact")))
    }

    private fun ensureInitialized() { check(initialized) { "ZoyaSessionManager is not initialized" } }
    private fun isConnected(): Boolean = client != null
    private fun reconnect() { client?.disconnect(); connectInternal() }
    private fun scheduleReconnect() { /* existing reconnect implementation */ }
    private fun startForegroundService() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                ContextCompat.startForegroundService(app, Intent(app, ZoyaForegroundService::class.java))
            }
        }
    }
    private fun stopForegroundService() { runCatching { app.stopService(Intent(app, ZoyaForegroundService::class.java)) } }
}
