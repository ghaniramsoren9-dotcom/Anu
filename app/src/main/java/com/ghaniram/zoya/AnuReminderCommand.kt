package com.ghaniram.zoya

import android.content.Context
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Handles natural-language reminder requests in Odia, Hindi, and English. */
object AnuReminderCommand {
    private val timeRegex = Regex("(?i)(?<!\\d)(2[0-3]|[01]?[0-9])(?::([0-5][0-9]))?(?!\\d)")

    fun trySchedule(context: Context, rawText: String): String? {
        var text = rawText
        val indicDigits = mapOf(
            '୦' to '0', '୧' to '1', '୨' to '2', '୩' to '3', '୪' to '4',
            '୫' to '5', '୬' to '6', '୭' to '7', '୮' to '8', '୯' to '9',
            '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
            '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
        )
        indicDigits.forEach { (k, v) -> text = text.replace(k, v) }

        val lower = text.lowercase(Locale.ROOT)
        val reminderIntent = lower.contains("remind") || lower.contains("reminder") || lower.contains("alarm") ||
            lower.contains("ମନେ ପକା") || lower.contains("ମନେରଖ") || lower.contains("ରିମାଇଣ୍ଡର") || lower.contains("ଆଲାରାମ")
        if (!reminderIntent) return null

        val isPm = lower.contains("ରାତି") || lower.contains("ସନ୍ଧ୍ୟା") || lower.contains("ଅପରାହ୍ନ") ||
            lower.contains("रात") || lower.contains("शाम") || lower.contains("pm")
        val isAm = lower.contains("ସକାଳ") || lower.contains("सुबह") || lower.contains("am")

        val match = timeRegex.find(text) ?: return null

        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()

        if (isPm && hour < 12) {
            hour += 12
        } else if (isAm && hour == 12) {
            hour = 0
        }

        val ampm = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        val timeLabel = String.format(Locale.US, "%d:%02d %s", hour12, minute, ampm)

        val title = text
            .replace(timeRegex, "")
            .replace(Regex("(?i)\\b(set|create|make|give|put|a|an|the|reminder|alarm|remind|me|at|for|please|ମୋତେ|ମନେ|ରିମାଇଣ୍ଡର|ଆଲାରାମ|ସେଟ|କରିଦିଅ|ରାତି|ସନ୍ଧ୍ୟା|ସକାଳ|ଅପରାହ୍ନ|ଟାରେ|ଟା|ବେଳେ)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', '?', '।')
            .ifBlank { "Anu reminder" }

        // Immediately add to ZoyaSessionManager so it is saved to storage and shown in Tasks UI!
        ZoyaSessionManager.addTask(title, timeLabel)
        return "ଠିକ୍ ଅଛି ପ୍ରିୟ, $timeLabel ପାଇଁ ମୁଁ \"$title\" ରିମାଇଣ୍ଡର ସେଟ୍ କରିଦେଲି।"
    }
}
