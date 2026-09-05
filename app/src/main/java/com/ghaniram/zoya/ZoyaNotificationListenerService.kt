package com.ghaniram.zoya

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Notification access is user-enabled. Supports reading, opening, dismissing and reply actions exposed by apps. */
class ZoyaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) = Unit

    data class NotificationItem(val key: String, val packageName: String, val title: String, val text: String, val hasReply: Boolean)

    fun getNotifications(): List<NotificationItem> = activeNotifications?.mapNotNull { sbn ->
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) null else NotificationItem(sbn.key, sbn.packageName, title, text, findReplyAction(sbn) != null)
    }.orEmpty()

    fun readActiveNotifications(): List<String> = getNotifications().map {
        val reply = if (it.hasReply) " [reply available]" else ""
        "${it.packageName}: ${listOf(it.title, it.text).filter(String::isNotBlank).joinToString(" — ")}$reply"
    }

    /** Replies only through a RemoteInput action already exposed by the target notification. */
    fun replyToNotification(query: String, replyText: String): String {
        if (replyText.isBlank()) return "reply text is missing"
        val notifications = activeNotifications?.toList().orEmpty()
        val normalized = normalize(query)
        val candidates = notifications.filter { sbn ->
            val extras = sbn.notification.extras
            val title = normalize(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
            val text = normalize(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
            normalized.isBlank() || title.contains(normalized) || text.contains(normalized) || sbn.packageName.lowercase().contains(normalized)
        }
        val target = candidates.firstOrNull { findReplyAction(it) != null } ?: return "I couldn't find a notification with a supported reply action"
        val action = findReplyAction(target) ?: return "this notification does not expose a reply action"
        val remoteInputs = action.remoteInputs ?: return "this notification does not expose a reply field"
        return try {
            val results = Bundle()
            remoteInputs.forEach { input -> results.putCharSequence(input.resultKey, replyText) }
            val fillIn = Intent().apply { RemoteInput.addResultsToIntent(remoteInputs, this, results) }
            action.actionIntent.send(this, 0, fillIn)
            "reply sent"
        } catch (e: Exception) { "could not send notification reply: ${e.message ?: "unknown error"}" }
    }

    fun openNotification(query: String): String {
        val target = findNotification(query) ?: return "I couldn't find that notification"
        return try { target.notification.contentIntent?.send(); "opened notification" } catch (e: Exception) { "could not open notification: ${e.message ?: "unknown error"}" }
    }

    fun dismiss(packageName: String): Boolean = activeNotifications?.firstOrNull { it.packageName == packageName }?.let { cancelNotification(it.key); true } ?: false

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

    private fun findReplyAction(sbn: StatusBarNotification): Notification.Action? = sbn.notification.actions?.firstOrNull { action ->
        val inputs = action.remoteInputs
        !inputs.isNullOrEmpty()
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("\\s+"), " ").trim()

    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    companion object {
        @Volatile var instance: ZoyaNotificationListenerService? = null
            private set
    }

    override fun onListenerConnected() { super.onListenerConnected(); instance = this }
}
