package com.ghaniram.zoya

/** Safe, predefined UI vocabulary for Gemini-driven responses. */
sealed interface DynamicUiModel {
    data class Weather(val title: String, val condition: String, val temperature: String?, val action: String? = null) : DynamicUiModel
    data class TaskList(val title: String, val tasks: List<Task>) : DynamicUiModel
    data class Reminder(val title: String, val time: String, val details: String? = null) : DynamicUiModel
    data class Navigation(val title: String, val destination: String, val distance: String? = null, val action: String = "Open Maps") : DynamicUiModel
    data class Confirmation(val title: String, val message: String, val confirmLabel: String = "Confirm", val cancelLabel: String = "Cancel") : DynamicUiModel
    data class Progress(val title: String, val message: String, val progress: Float? = null) : DynamicUiModel
    data class Error(val title: String, val message: String, val retryLabel: String = "Retry") : DynamicUiModel

    data class Task(val title: String, val completed: Boolean)
}

/** Parser for a deliberately small, deterministic response protocol. */
object DynamicUiParser {
    fun parse(text: String): DynamicUiModel? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val header = lines.firstOrNull()?.uppercase() ?: return null
        val body = lines.drop(1)
        fun value(key: String) = body.firstOrNull { it.startsWith("$key:", ignoreCase = true) }
            ?.substringAfter(":")?.trim()?.takeIf { it.isNotEmpty() }

        return when (header) {
            "[ANU_WEATHER]" -> DynamicUiModel.Weather(value("title") ?: "Weather", value("condition") ?: "", value("temperature"), value("action"))
            "[ANU_REMINDER]" -> DynamicUiModel.Reminder(value("title") ?: "Reminder", value("time") ?: "", value("details"))
            "[ANU_NAVIGATION]" -> DynamicUiModel.Navigation(value("title") ?: "Navigation", value("destination") ?: "", value("distance"), value("action") ?: "Open Maps")
            "[ANU_CONFIRM]" -> DynamicUiModel.Confirmation(value("title") ?: "Confirm action", value("message") ?: "", value("confirm") ?: "Confirm", value("cancel") ?: "Cancel")
            "[ANU_PROGRESS]" -> DynamicUiModel.Progress(value("title") ?: "Working", value("message") ?: "", value("progress")?.toFloatOrNull()?.coerceIn(0f, 1f))
            "[ANU_ERROR]" -> DynamicUiModel.Error(value("title") ?: "Something went wrong", value("message") ?: "", value("retry") ?: "Retry")
            "[ANU_TASKS]" -> DynamicUiModel.TaskList(value("title") ?: "Tasks", body.filter { it.startsWith("-") }.mapNotNull {
                val raw = it.removePrefix("-").trim()
                if (raw.isEmpty()) null else DynamicUiModel.Task(raw.removePrefix("[x]").removePrefix("[X]").trim(), raw.startsWith("[x]", true))
            })
            else -> null
        }
    }
}
