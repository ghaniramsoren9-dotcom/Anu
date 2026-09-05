from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"
client = PKG / "GeminiLiveClient.kt"
session = PKG / "ZoyaSessionManager.kt"

c = client.read_text(encoding="utf-8")
s = session.read_text(encoding="utf-8")

# Gemini Live session resumption. Keep the callback optional so existing
# lightweight Live clients (for example one-shot Vision callbacks) remain
# source-compatible while the session manager can consume resumption handles.
if "fun onSessionResumptionHandle(handle: String, resumable: Boolean)" not in c:
    c = c.replace(
        "fun onToolCall(name: String, args: JSONObject, id: String)\n",
        "fun onToolCall(name: String, args: JSONObject, id: String)\n        fun onSessionResumptionHandle(handle: String, resumable: Boolean) = Unit\n",
        1,
    )
if "private var resumeHandle: String?" not in c:
    c = c.replace("private var terminalErrorSent = false", "private var terminalErrorSent = false\n    private var resumeHandle: String? = null", 1)
c = c.replace(
    "fun connect(systemInstruction: String, tools: JSONArray) {",
    "fun connect(systemInstruction: String, tools: JSONArray, sessionResumptionHandle: String? = null) {",
    1,
)
c = c.replace(
    "setupComplete = false\n        terminalErrorSent = false\n        val url =",
    "setupComplete = false\n        terminalErrorSent = false\n        resumeHandle = sessionResumptionHandle?.takeIf { it.isNotBlank() }\n        val url =",
    1,
)
if 'put("sessionResumption"' not in c:
    c = c.replace(
        '                    put("inputAudioTranscription", JSONObject())\n',
        '                    put("inputAudioTranscription", JSONObject())\n                    put("sessionResumption", JSONObject().apply { resumeHandle?.let { put("handle", it) } })\n',
        1,
    )
if 'json.optJSONObject("sessionResumptionUpdate")' not in c:
    c = c.replace(
        '        if (json.has("setupComplete")) {',
        '''        json.optJSONObject("sessionResumptionUpdate")?.let { update ->
            val handle = update.optString("newHandle")
            val resumable = update.optBoolean("resumable", false)
            if (resumable && handle.isNotBlank()) {
                resumeHandle = handle
                callbacks.onSessionResumptionHandle(handle, true)
            } else if (!resumable) {
                callbacks.onSessionResumptionHandle("", false)
            }
        }
        if (json.has("setupComplete")) {''',
        1,
    )
client.write_text(c, encoding="utf-8")

# Replace the reconnect placeholder with real exponential backoff and persist
# the latest session resumption handle.
if "private var reconnectAttempt = 0" not in s:
    s = s.replace(
        "private var modelSpeaking = false",
        '''private var modelSpeaking = false
    private var reconnectAttempt = 0
    private var resumeHandle: String? = null
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null''',
        1,
    )
if "/* existing reconnect implementation */" in s:
    s = s.replace(
        '    private fun scheduleReconnect() { /* existing reconnect implementation */ }',
        '''    private fun scheduleReconnect() {
        if (!prefs.getBoolean("active", false)) return
        reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }
        val delay = when (reconnectAttempt.coerceAtMost(4)) {
            0 -> 1000L
            1 -> 2000L
            2 -> 4000L
            3 -> 8000L
            else -> 15000L
        }
        reconnectAttempt++
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
        val task = Runnable {
            if (prefs.getBoolean("active", false)) connectInternal()
        }
        reconnectRunnable = task
        reconnectHandler.postDelayed(task, delay)
    }''',
        1,
    )
s = s.replace(
    'initialized = true; val savedLanguage =',
    'initialized = true; resumeHandle = prefs.getString("live_resume_handle", "")?.takeIf { it.isNotBlank() }; val savedLanguage =',
    1,
)
s = s.replace(
    'override fun onConnected() { audioEngine?.startPlayback(); audioEngine?.startRecording(); startAudioWatchdog(); modelSpeaking = false;',
    'override fun onConnected() { reconnectAttempt = 0; audioEngine?.startPlayback(); audioEngine?.startRecording(); startAudioWatchdog(); modelSpeaking = false;',
    1,
)
if 'override fun onSessionResumptionHandle' not in s:
    s = s.replace(
        'override fun onToolCall(name: String, args: JSONObject, id: String) = handleToolCall(name, args, id)',
        '''override fun onToolCall(name: String, args: JSONObject, id: String) = handleToolCall(name, args, id)
        override fun onSessionResumptionHandle(handle: String, resumable: Boolean) {
            resumeHandle = handle.takeIf { resumable && it.isNotBlank() }
            prefs.edit().putString("live_resume_handle", resumeHandle ?: "").apply()
        }''',
        1,
    )
s = s.replace(
    'client?.connect(buildSystemInstruction(), buildToolDeclarations())',
    'client?.connect(buildSystemInstruction(), buildToolDeclarations(), resumeHandle)',
    1,
)
# Ensure socket-close recovery is scheduled, not just displayed.
s = s.replace(
    'if (prefs.getBoolean("active", false)) _state.update { it.copy(connectionState = ConnectionState.CONNECTING) } else _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }',
    'if (prefs.getBoolean("active", false)) { _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }; scheduleReconnect() } else _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }',
    1,
)
# Explicit disconnect cancels pending reconnect and clears the resume handle.
s = s.replace(
    'fun disconnect() { ensureInitialized(); stopAudioWatchdog(); prefs.edit().putBoolean("active", false).apply();',
    'fun disconnect() { ensureInitialized(); stopAudioWatchdog(); reconnectRunnable?.let { reconnectHandler.removeCallbacks(it) }; reconnectRunnable = null; reconnectAttempt = 0; resumeHandle = null; prefs.edit().putBoolean("active", false).remove("live_resume_handle").apply();',
    1,
)
session.write_text(s, encoding="utf-8")
print("Applied Live session resumption and reconnect recovery.")
