package com.ghaniram.zoya

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

/** UI facade. The session manager owns the Gemini/audio session across Activity recreation. */
class ZoyaViewModel(application: Application) : AndroidViewModel(application) {
    init { ZoyaSessionManager.initialize(application) }
    val state: StateFlow<ZoyaUiState> = ZoyaSessionManager.state
    fun setLanguage(lang: ZoyaLanguage) = ZoyaSessionManager.setLanguage(lang)
    fun connect() = ZoyaSessionManager.connect()
    fun startVisionSession() = ZoyaSessionManager.startVisionSession()
    fun sendVisionFrame(base64Jpeg: String) = ZoyaSessionManager.sendVisionFrame(base64Jpeg)
    fun disconnect() = ZoyaSessionManager.disconnect()

    /**
     * A cold app used to send the first message immediately after creating the
     * WebSocket. Gemini drops realtimeInput until setupComplete. Wait for the
     * session to reach LISTENING/SPEAKING before delivering the message.
     */
    fun sendText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val lower = clean.lowercase()
            val deviceRequest = lower.contains("battery") || lower.contains("ବ୍ୟାଟେରୀ") ||
                lower.contains("cpu") || lower.contains("gpu") || lower.contains("ram") ||
                lower.contains("storage") || lower.contains("device information") ||
                lower.contains("device info") || lower.contains("phone information") ||
                lower.contains("phone info") || lower.contains("ମୋ ଫୋନ") || lower.contains("phone")
            if (deviceRequest) {
                val telemetry = withContext(Dispatchers.IO) { DeviceInfoProvider.snapshot(getApplication()) }
                ensureSessionReady()
                ZoyaSessionManager.sendText("$clean\n\n[LOCAL DEVICE TELEMETRY — use these fresh values as ground truth; do not invent or override them]\n$telemetry")
            } else {
                ensureSessionReady()
                ZoyaSessionManager.sendText(clean)
            }
        }
    }

    private suspend fun ensureSessionReady() {
        val current = state.value.connectionState
        if (current == ConnectionState.DISCONNECTED) {
            ZoyaSessionManager.connect()
        }
        withTimeoutOrNull(12_000L) {
            state.first {
                it.connectionState == ConnectionState.LISTENING ||
                    it.connectionState == ConnectionState.IDLE ||
                    it.connectionState == ConnectionState.SPEAKING
            }
        }
    }

    fun clearMemories() = ZoyaSessionManager.clearMemories()
    fun clearChatHistory() = ZoyaSessionManager.clearChatHistory()
    fun dismissError() = ZoyaSessionManager.dismissError()
    fun addTask(title: String, time: String) = ZoyaSessionManager.addTask(title, time)
    fun toggleTask(id: String) = ZoyaSessionManager.toggleTask(id)
    fun deleteTask(id: String) = ZoyaSessionManager.deleteTask(id)
    fun setVisionActive(active: Boolean) = ZoyaSessionManager.setVisionActive(active)
    fun setVisionDescription(desc: String) = ZoyaSessionManager.setVisionDescription(desc)
    fun analyzeVisionFrame(jpegBytes: ByteArray, prompt: String, onComplete: ((String) -> Unit)? = null) =
        ZoyaSessionManager.analyzeVisionFrame(jpegBytes, prompt, onComplete)
    fun onApiKeyUpdated(newKey: String) = ZoyaSessionManager.onApiKeyUpdated(newKey)
    fun onSettingsUpdated() = ZoyaSessionManager.onSettingsUpdated()
    override fun onCleared() { super.onCleared() }
}
