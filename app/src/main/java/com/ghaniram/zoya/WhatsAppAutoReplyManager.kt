package com.ghaniram.zoya

import android.app.Notification
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates WhatsApp notification auto-replies.
 * Owns filtering, rate-limits, chat allow/deny lists, OTP protection,
 * and the final notification reply action.
 */
class WhatsAppAutoReplyManager(private val settings: AnuSettingsStore) {
    private val recentKeys = ConcurrentHashMap<String, Long>()
    private val perChatCount = ConcurrentHashMap<String, Int>()
    private var dayStartMs = startOfDay()
    private var dayCount = 0

    private val cooldownMs = 30_000L
    private val maxPerChat = 12
    private val maxPerDay = 60

    fun shouldHandle(sbn: StatusBarNotification): Boolean {
        if (!settings.whatsAppAutoReplyEnabled) return false
        if (!isWhatsAppPackage(sbn.packageName)) return false

        val skills = settings.enabledSkills
        if ("pro-whatsapp" !in skills && "whatsapp-pro" !in skills) return false

        if (settings.replyOnlyWhileAsleep) {
            val connected = ZoyaSessionManager.state.value.connectionState != ConnectionState.DISCONNECTED
            if (connected) return false
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = listOf(text, bigText).firstOrNull { it.isNotBlank() }.orEmpty()

        if (title.isBlank() && body.isBlank()) return false

        if (!settings.replyIncludeGroups) {
            if (title.matches(Regex(".*\\(\\d+\\s*(members?|participants?)\\).*", RegexOption.IGNORE_CASE))) return false
        }

        val never = settings.replyNeverChats.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val only = settings.replyOnlyChats.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val titleLower = title.lowercase()
        if (never.any { titleLower.contains(it) }) return false
        if (only.isNotEmpty() && only.none { titleLower.contains(it) }) return false

        if (looksLikeOtpOrBank(body) || looksLikeOtpOrBank(title)) return false

        resetDayIfNeeded()
        if (dayCount >= maxPerDay) return false

        val chatKey = title.ifBlank { sbn.key }
        val chatCount = perChatCount.getOrDefault(chatKey, 0)
        if (chatCount >= maxPerChat) return false

        val now = SystemClock.elapsedRealtime()
        val previous = recentKeys[sbn.key]
        if (previous != null && now - previous < cooldownMs) return false

        recentKeys[sbn.key] = now
        return true
    }

    fun markSent(sbn: StatusBarNotification) {
        resetDayIfNeeded()
        dayCount++
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val chatKey = title.ifBlank { sbn.key }
        perChatCount[chatKey] = perChatCount.getOrDefault(chatKey, 0) + 1
    }

    fun markFailed(sbn: StatusBarNotification) {
        recentKeys.remove(sbn.key)
    }

    fun buildReplyText(incomingTitle: String, incomingBody: String, generated: String?): String {
        val base = generated?.trim().orEmpty().ifBlank {
            settings.replyFallbackNoInternet.trim().ifBlank { "Busy right now, will reply soon." }
        }
        val note = settings.replySignatureNote.trim()
        return if (note.isNotBlank() && !base.contains(note)) "$base\n$note" else base
    }

    fun sendReply(service: ZoyaNotificationListenerService, sbn: StatusBarNotification, reply: String): String {
        if (reply.isBlank()) {
            markFailed(sbn)
            return "reply text is missing"
        }
        val result = service.replyToNotificationByKey(sbn.key, reply)
        if (result == "reply sent") markSent(sbn) else markFailed(sbn)
        return result
    }

    fun extractMessage(sbn: StatusBarNotification): Pair<String, String> {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = when {
            big.isNotBlank() -> big
            text.isNotBlank() -> text
            else -> ""
        }
        return title to body
    }

    private fun isWhatsAppPackage(packageName: String): Boolean =
        packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"

    private fun looksLikeOtpOrBank(text: String): Boolean {
        val t = text.lowercase()
        if (t.isBlank()) return false
        val otpPatterns = listOf(
            "otp", "one time", "one-time", "verification code", "security code",
            "auth code", "authenticate", "do not share", "don't share",
            "bank", "upi", "debit", "credit card", "a/c", "account ending",
            "transaction", "\u20b9", "inr ", "rs.", "rs "
        )
        if (otpPatterns.any { t.contains(it) }) return true
        if (Regex("\\b\\d{4,8}\\b").containsMatchIn(t) && (t.contains("code") || t.contains("otp") || t.contains("pin"))) return true
        return false
    }

    private fun startOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun resetDayIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - dayStartMs > 24 * 60 * 60 * 1000L) {
            dayStartMs = startOfDay()
            dayCount = 0
            perChatCount.clear()
        }
    }
}
