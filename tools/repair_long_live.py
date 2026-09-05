from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PKG = ROOT / "app/src/main/java/com/ghaniram/zoya"
audio = PKG / "AudioEngine.kt"
session = PKG / "ZoyaSessionManager.kt"

# Make AudioEngine report a real capture failure, not just AudioRecord's state.
a = audio.read_text(encoding="utf-8")
if "private val lastMicReadNanos = AtomicLong(0L)" not in a:
    a = a.replace(
        "private val totalFramesWritten = AtomicLong(0L)",
        "private val totalFramesWritten = AtomicLong(0L)\n    private val lastMicReadNanos = AtomicLong(0L)",
        1,
    )

# Reset the health timestamp whenever a fresh recorder starts.
a = a.replace(
    "audioRecord = record\n        try { record.startRecording() }",
    "audioRecord = record\n        lastMicReadNanos.set(System.nanoTime())\n        try { record.startRecording() }",
    1,
)

# A successful PCM read is the strongest proof that the microphone pipeline is alive.
a = a.replace(
    "if (read > 0) {\n                    var sum = 0.0;",
    "if (read > 0) {\n                    lastMicReadNanos.set(System.nanoTime())\n                    var sum = 0.0;",
    1,
)

if "fun isRecordingHealthy(): Boolean" in a:
    start = a.index("    fun isRecordingHealthy(): Boolean {")
    end = a.index("    fun stopRecording() {", start)
    a = a[:start] + '''    fun isRecordingHealthy(): Boolean {
        val record = audioRecord ?: return false
        val job = recordJob ?: return false
        if (!job.isActive || record.state != AudioRecord.STATE_INITIALIZED || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
        val lastRead = lastMicReadNanos.get()
        return lastRead > 0L && System.nanoTime() - lastRead < 5_000_000_000L
    }

''' + a[end:]
else:
    marker = "    fun stopRecording() {"
    method = '''    fun isRecordingHealthy(): Boolean {
        val record = audioRecord ?: return false
        val job = recordJob ?: return false
        if (!job.isActive || record.state != AudioRecord.STATE_INITIALIZED || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
        val lastRead = lastMicReadNanos.get()
        return lastRead > 0L && System.nanoTime() - lastRead < 5_000_000_000L
    }

'''
    if marker not in a:
        raise SystemExit("AudioEngine recording marker not found")
    a = a.replace(marker, method + marker, 1)

a = a.replace("audioRecord = null; onInputLevel(0f)", "audioRecord = null; lastMicReadNanos.set(0L); onInputLevel(0f)", 1)
audio.write_text(a, encoding="utf-8")

# Make the session manager recover both dead microphones and server-side/socket disconnects.
s = session.read_text(encoding="utf-8")
if "private var audioWatchdogRunning = false" not in s:
    s = s.replace(
        "private var modelSpeaking = false",
        """private var modelSpeaking = false
    private var audioWatchdogRunning = false
    private val audioWatchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val audioWatchdog = object : Runnable {
        override fun run() {
            if (!audioWatchdogRunning) return
            if (prefs.getBoolean("active", false) && !modelSpeaking && client != null) {
                val engine = audioEngine
                if (engine != null && !engine.isRecordingHealthy()) {
                    engine.stopRecording()
                    engine.startRecording()
                    if (engine.isRecordingHealthy()) {
                        _state.update { it.copy(connectionState = ConnectionState.LISTENING, error = null) }
                    } else {
                        _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }
                        scheduleReconnect()
                    }
                }
            }
            audioWatchdogHandler.postDelayed(this, 2500L)
        }
    }""",
        1,
    )

if "private fun startAudioWatchdog()" not in s:
    marker = "    fun disconnect() {"
    helpers = '''    private fun startAudioWatchdog() {
        audioWatchdogRunning = true
        audioWatchdogHandler.removeCallbacks(audioWatchdog)
        audioWatchdogHandler.postDelayed(audioWatchdog, 2500L)
    }

    private fun stopAudioWatchdog() {
        audioWatchdogRunning = false
        audioWatchdogHandler.removeCallbacks(audioWatchdog)
    }

'''
    if marker not in s:
        raise SystemExit("Session disconnect marker not found")
    s = s.replace(marker, helpers + marker, 1)

s = s.replace(
    "audioEngine?.startPlayback(); audioEngine?.startRecording(); modelSpeaking = false",
    "audioEngine?.startPlayback(); audioEngine?.startRecording(); startAudioWatchdog(); modelSpeaking = false",
    1,
)
s = s.replace(
    "override fun onDisconnected() { modelSpeaking = false; audioEngine?.stopRecording(); if (prefs.getBoolean(\"active\", false)) _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }",
    "override fun onDisconnected() { stopAudioWatchdog(); modelSpeaking = false; audioEngine?.stopRecording(); if (prefs.getBoolean(\"active\", false)) { _state.update { it.copy(connectionState = ConnectionState.CONNECTING) }; scheduleReconnect() }",
    1,
)
s = s.replace(
    "override fun onError(message: String) { modelSpeaking = false; audioEngine?.stopRecording();",
    "override fun onError(message: String) { stopAudioWatchdog(); modelSpeaking = false; audioEngine?.stopRecording();",
    1,
)
s = s.replace(
    "fun disconnect() { ensureInitialized(); prefs.edit().putBoolean(\"active\", false).apply();",
    "fun disconnect() { ensureInitialized(); stopAudioWatchdog(); prefs.edit().putBoolean(\"active\", false).apply();",
    1,
)
s = s.replace(
    "fun connect() { ensureInitialized(); prefs.edit().putBoolean(\"active\", true).apply();",
    "fun connect() { ensureInitialized(); stopAudioWatchdog(); prefs.edit().putBoolean(\"active\", true).apply();",
    1,
)

session.write_text(s, encoding="utf-8")
print("Hardened Live mic health detection, automatic recorder recovery, and reconnect after Live disconnects.")
