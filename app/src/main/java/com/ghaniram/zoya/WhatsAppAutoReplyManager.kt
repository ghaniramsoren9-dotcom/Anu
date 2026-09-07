package com.ghaniram.zoya

import android.os.SystemClock
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates WhatsApp notification auto-replies without hijacking Anu's live voice session.
 * AI generation is deliberately injected by the caller; this class owns filtering,
 * duplicate/rate-limit protection and the final notification reply action.
 */
class WhatsAppAutoReplyManager(private val settings: AnuSettingsStore) {
    private val recent = ConcurrentHashMap<String, Long>()
    private val cooldownMs = 30_000L

    fun shouldHandle(sbn: StatusBarNotification): Boolean {
        if (!isWhatsAppPackage(sbn.packageName)) return false
        val skills = settings.enabledSkills
        if ("pro-whatsapp" !in skills && "whatsapp-pro" !in skills) return false
        val key = sbn.key
        val now = SystemClock.elapsedRealtime()
        val previous = recent[key]
        if (previous != null && now - previous < cooldownMs) return false
        recent[key] = now
        return true
    }

    fun markFailed(sbn: StatusBarNotification) {
        recent.remove(sbn.key)
    }

    fun sendReply(service: ZoyaNotificationListenerService, sbn: StatusBarNotification, reply: String): String {
        if (reply.isBlank()) {
            markFailed(sbn)
            return "reply text is missing"
        }
        return service.replyToNotification(sbn.key, reply)
    }

    private fun isWhatsAppPackage(packageName: String): Boolean =
        packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"
}
