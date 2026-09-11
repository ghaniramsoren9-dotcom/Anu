package com.ghaniram.zoya

import android.content.Context
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Handles simple natural-language reminder requests before they reach the model. */
object AnuReminderCommand {
    private val timeRegex = Regex("(?i)\\b(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(am|pm)\\b")

    fun trySchedule(context: Context, text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        val reminderIntent = lower.contains("remind") || lower.contains("reminder") || lower.contains("alarm") ||
            lower.contains("ମନେ ପକା") || lower.contains("ମନେରଖ") || lower.contains("ରିମାଇଣ୍ଡର") || lower.contains("ଆଲାରାମ")
        if (!reminderIntent) return null
        val match = timeRegex.find(text) ?: return null

        val hour12 = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val ampm = match.groupValues[3].lowercase(Locale.ROOT)
        val hour24 = when {
            ampm == "am" && hour12 == 12 -> 0
            ampm == "pm" && hour12 != 12 -> hour12 + 12
            else -> hour12
        }
        val timeLabel = String.format(Locale.US, "%d:%02d %s", hour12, minute, ampm.uppercase(Locale.ROOT))
        val title = text
            .replace(timeRegex, "")
            .replace(Regex("(?i)\\b(set|create|make|give|put|a|an|the|reminder|alarm|remind|me|at|for|please|ମୋତେ|ମନେ|ରିମାଇଣ୍ଡର|ଆଲାରାମ|ସେଟ|କରିଦିଅ)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', '?', '।')
            .ifBlank { "Anu reminder" }

        // Add task to ZoyaSessionManager so it is saved and visibly listed in the Tasks section!
        ZoyaSessionManager.addTask(title, timeLabel)
        return "ଠିକ୍ ଅଛି ପ୍ରିୟ, $timeLabel ପାଇଁ ମୁଁ \"$title\" ରିମାଇଣ୍ଡର ସେଟ୍ କରିଦେଲି।"
    }
}
