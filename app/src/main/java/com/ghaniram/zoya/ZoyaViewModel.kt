package com.ghaniram.zoya

import android.app.Application
import android.util.Base64
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

    /**
     * One-shot camera analysis is intentionally routed into the SAME Anu Live session.
     * There is no secondary Gemini vision client anymore: the frame becomes Anu's
     * visual input and the existing Anu audio pipeline speaks the answer.
     */
    fun analyzeVisionFrame(jpegBytes: ByteArray, prompt: String, onComplete: ((String) -> Unit)? = null) {
        if (jpegBytes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ensureSessionReady()
            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            ZoyaSessionManager.sendVisionFrame(base64)
            val language = when (state.value.language) {
                ZoyaLanguage.ODIA -> "Respond in natural Odia."
                ZoyaLanguage.HINDI -> "Respond in natural Hindi."
                ZoyaLanguage.SANTALI -> "Respond in natural Santali."
                ZoyaLanguage.ENGLISH -> "Respond in clear natural English."
            }
            ZoyaSessionManager.sendText(
                "[LIVE CAMERA VISION] $prompt $language " +
                    "Use the latest camera frame as your only visual evidence. " +
                    "Answer aloud as Anu. Do not use screen/accessibility data and do not invent anything outside the visible camera frame."
            )
            withContext(Dispatchers.Main) {
                onComplete?.invoke("Anu is analyzing the live camera view and will answer aloud.")
            }
        }
    }

    fun onApiKeyUpdated(newKey: String) = ZoyaSessionManager.onApiKeyUpdated(newKey)
    fun onSettingsUpdated() = ZoyaSessionManager.onSettingsUpdated()
    override fun onCleared() { super.onCleared() }
}
