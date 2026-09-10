package com.ghaniram.zoya

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
    init {
        ZoyaSessionManager.initialize(application)
        // A newly-created Activity/ViewModel must never resurrect the previous
        // microphone session. Listening is started only by an explicit user ON action.
        ZoyaSessionManager.disconnect()
    }
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
            val reminderReply = AnuReminderCommand.trySchedule(getApplication(), clean)
            if (reminderReply != null) {
                ZoyaSessionManager.sendText(reminderReply)
                return@launch
            }
            val lower = clean.lowercase()
            val deviceRequest = lower.contains("battery") || lower.contains("ବ୍ୟାଟେରୀ") ||
                lower.contains("cpu") || lower.contains("gpu") || lower.contains("ram") ||
                lower.contains("storage") || lower.contains("device information") ||
                lower.contains("device info") || lower.contains("phone information") ||
                lower.contains("phone info") || lower.contains("ମୋ ଫୋନ") || lower.contains("phone")
            if (deviceRequest) DeviceQueryContext.set(clean)
            ensureSessionReady()
            ZoyaSessionManager.sendText(clean)
        }
    }

    private suspend fun ensureSessionReady() {
        val current = state.value.connectionState
        if (current == ConnectionState.DISCONNECTED) ZoyaSessionManager.connect()
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

    fun addTask(title: String, time: String) {
        val cleanTitle = title.trim()
        val cleanTime = time.trim()
        if (cleanTitle.isBlank() || cleanTime.isBlank()) return
        ZoyaSessionManager.addTask(cleanTitle, cleanTime)
        val task = state.value.tasks.lastOrNull { it.title == cleanTitle && it.timeLabel == cleanTime && !it.isCompleted }
        val taskId = task?.id ?: "${cleanTitle.hashCode()}_${cleanTime.hashCode()}"
        AnuTaskAlarmScheduler.schedule(getApplication(), taskId, cleanTitle, cleanTime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getApplication<Application>().getSystemService(AlarmManager::class.java)
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                runCatching {
                    getApplication<Application>().startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${getApplication<Application>().packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
        }
    }

    fun toggleTask(id: String) {
        val task = state.value.tasks.firstOrNull { it.id == id }
        ZoyaSessionManager.toggleTask(id)
        if (task != null) {
            if (task.isCompleted) AnuTaskAlarmScheduler.schedule(getApplication(), task.copy(isCompleted = false))
            else AnuTaskAlarmScheduler.cancel(getApplication(), task.id)
        }
    }

    fun deleteTask(id: String) {
        AnuTaskAlarmScheduler.cancel(getApplication(), id)
        ZoyaSessionManager.deleteTask(id)
    }

    fun setVisionActive(active: Boolean) = ZoyaSessionManager.setVisionActive(active)
    fun setVisionDescription(desc: String) = ZoyaSessionManager.setVisionDescription(desc)

    /** One-shot camera analysis is routed into the SAME Anu Live session. */
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
            withContext(Dispatchers.Main) { onComplete?.invoke("Anu is analyzing the live camera view and will answer aloud.") }
        }
    }

    fun onApiKeyUpdated(newKey: String) = ZoyaSessionManager.onApiKeyUpdated(newKey)
    fun onSettingsUpdated() = ZoyaSessionManager.onSettingsUpdated()
    override fun onCleared() { super.onCleared() }
}
