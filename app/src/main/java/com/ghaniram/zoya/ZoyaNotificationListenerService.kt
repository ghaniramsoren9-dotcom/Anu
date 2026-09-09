package com.ghaniram.zoya

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Notification access is user-enabled. Supports reading, opening, dismissing, reply, and WhatsApp auto-reply. */
class ZoyaNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        tryHandleWhatsAppAutoReply(sbn)
    }

    private fun tryHandleWhatsAppAutoReply(sbn: StatusBarNotification) {
        val settings = runCatching { AnuSettingsStore.getInstance(applicationContext) }.getOrNull() ?: return
        val manager = WhatsAppAutoReplyManager(settings)
        if (!manager.shouldHandle(sbn)) return

        val (title, body) = manager.extractMessage(sbn)
        if (body.isBlank() && title.isBlank()) return

        scope.launch {
            val generated = generateReplyWithGemini(settings, title, body)
            val replyText = manager.buildReplyText(title, body, generated)
            mainHandler.post {
                val result = manager.sendReply(this@ZoyaNotificationListenerService, sbn, replyText)
                Log.d(TAG, "WhatsApp auto-reply result: $result")
            }
        }
    }

    private fun generateReplyWithGemini(settings: AnuSettingsStore, title: String, body: String): String? {
        val apiKey = settings.customGeminiKey.trim()
        if (apiKey.isBlank()) return null

        val instructions = settings.replyInstructions.ifBlank {
            "Politely say I am busy right now and will reply myself soon. Keep it short, warm and friendly."
        }
        val prompt = buildString {
            append("You write short WhatsApp auto-replies.\n")
            append("Instructions: $instructions\n")
            append("Incoming from: $title\n")
            append("Message: $body\n")
            append("Reply with ONLY the message text. No quotes, no explanation. Match the language of the incoming message.")
        }

        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 120)
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                val text = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                text?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini reply generation failed: ${e.message}")
            null
        }
    }

    data class NotificationItem(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val hasReply: Boolean
    )

    fun getNotifications(): List<NotificationItem> = activeNotifications?.mapNotNull { sbn ->
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) null
        else NotificationItem(sbn.key, sbn.packageName, title, text, findReplyAction(sbn) != null)
    }.orEmpty()

    fun readActiveNotifications(): List<String> = getNotifications().map {
        val reply = if (it.hasReply) " [reply available]" else ""
        "${it.packageName}: ${listOf(it.title, it.text).filter(String::isNotBlank).joinToString(" \u2014 ")}$reply"
    }

    fun replyToNotification(query: String, replyText: String): String {
        if (replyText.isBlank()) return "reply text is missing"
        val notifications = activeNotifications?.toList().orEmpty()
        val normalized = normalize(query)
        val candidates = notifications.filter { sbn ->
            val extras = sbn.notification.extras
            val title = normalize(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            val text = normalize(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            normalized.isBlank() || title.contains(normalized) || text.contains(normalized) ||
                sbn.packageName.lowercase().contains(normalized)
        }
        val target = candidates.firstOrNull { findReplyAction(it) != null }
            ?: return "I couldn't find a notification with a supported reply action"
        return replyToNotificationByKey(target.key, replyText)
    }

    fun replyToNotificationByKey(key: String, replyText: String): String {
        if (replyText.isBlank()) return "reply text is missing"
        val target = activeNotifications?.firstOrNull { it.key == key }
            ?: return "notification no longer available"
        val action = findReplyAction(target) ?: return "this notification does not expose a reply action"
        val remoteInputs = action.remoteInputs ?: return "this notification does not expose a reply field"
        return try {
            val results = Bundle()
            remoteInputs.forEach { input -> results.putCharSequence(input.resultKey, replyText) }
            val fillIn = Intent().apply { RemoteInput.addResultsToIntent(remoteInputs, this, results) }
            action.actionIntent.send(this, 0, fillIn)
            "reply sent"
        } catch (e: Exception) {
            "could not send notification reply: ${e.message ?: "unknown error"}"
        }
    }

    fun openNotification(query: String): String {
        val target = findNotification(query) ?: return "I couldn't find that notification"
        return try {
            target.notification.contentIntent?.send()
            "opened notification"
        } catch (e: Exception) {
            "could not open notification: ${e.message ?: "unknown error"}"
        }
    }

    fun dismiss(packageName: String): Boolean =
        activeNotifications?.firstOrNull { it.packageName == packageName }?.let {
            cancelNotification(it.key); true
        } ?: false

    private fun findNotification(query: String): StatusBarNotification? {
        val normalized = normalize(query)
        return activeNotifications?.firstOrNull { sbn ->
            if (normalized.isBlank()) true else {
                val extras = sbn.notification.extras
                normalize(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()).contains(normalized) ||
                    normalize(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()).contains(normalized) ||
                    sbn.packageName.lowercase().contains(normalized)
            }
        }
    }

    private fun findReplyAction(sbn: StatusBarNotification): Notification.Action? =
        sbn.notification.actions?.firstOrNull { action ->
            val inputs = action.remoteInputs
            !inputs.isNullOrEmpty()
        }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val TAG = "AnuNotifListener"
        @Volatile var instance: ZoyaNotificationListenerService? = null
            private set
    }
}
