package com.ghaniram.zoya

/**
 * Small, dependency-free planner for multi-step UI tasks.
 * It does not execute actions itself; ZoyaSessionManager remains the single
 * owner of tool execution and confirmation for sensitive operations.
 */
object AutonomousTaskPlanner {
    data class Step(
        val action: String,
        val target: String = "",
        val value: String = "",
        val requiresConfirmation: Boolean = false
    )

    data class Plan(val steps: List<Step>) {
        fun isEmpty(): Boolean = steps.isEmpty()
        fun size(): Int = steps.size
    }

    private val sensitiveActions = setOf(
        "send_message",
        "reply_notification",
        "delete",
        "purchase",
        "change_security",
        "lock_screen"
    )

    fun step(action: String, target: String = "", value: String = ""): Step {
        val normalized = action.trim().lowercase()
        return Step(
            action = normalized,
            target = target.trim(),
            value = value,
            requiresConfirmation = normalized in sensitiveActions
        )
    }

    fun plan(vararg steps: Step): Plan = Plan(steps.toList())

    fun requiresConfirmation(step: Step): Boolean = step.requiresConfirmation
}
