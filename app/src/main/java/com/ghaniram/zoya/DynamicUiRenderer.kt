package com.ghaniram.zoya

/**
 * UI-agnostic renderer contract. The Compose screen can map each model to its
 * existing Card/Surface primitives without allowing model output to execute code.
 */
object DynamicUiRenderer {
    fun accessibilitySummary(model: DynamicUiModel): String = when (model) {
        is DynamicUiModel.Weather -> "${model.title}: ${model.condition}${model.temperature?.let { ", $it" } ?: ""}"
        is DynamicUiModel.TaskList -> "${model.title}: ${model.tasks.count { it.completed }} of ${model.tasks.size} complete"
        is DynamicUiModel.Reminder -> "${model.title} at ${model.time}${model.details?.let { ": $it" } ?: ""}"
        is DynamicUiModel.Navigation -> "${model.title}: ${model.destination}${model.distance?.let { ", $it" } ?: ""}"
        is DynamicUiModel.Confirmation -> "${model.title}: ${model.message}"
        is DynamicUiModel.Progress -> "${model.title}: ${model.message}"
        is DynamicUiModel.Error -> "${model.title}: ${model.message}"
    }
}
