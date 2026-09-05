package com.ghaniram.zoya

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Resolves natural-language targets to actionable Accessibility nodes. */
object TargetResolver {
    data class Target(val node: AccessibilityNodeInfo, val score: Int, val bounds: Rect)

    fun resolve(root: AccessibilityNodeInfo?, query: String, longClick: Boolean = false): Target? {
        if (root == null || query.isBlank()) return null
        val target = normalize(query)
        if (target.isBlank()) return null
        val candidates = mutableListOf<Target>()
        collect(root, target, longClick, candidates)
        return candidates.maxByOrNull { it.score }
    }

    private fun collect(node: AccessibilityNodeInfo, target: String, longClick: Boolean, out: MutableList<Target>) {
        if (!node.isVisibleToUser) return
        val text = normalize(node.text?.toString().orEmpty())
        val desc = normalize(node.contentDescription?.toString().orEmpty())
        val id = normalize(node.viewIdResourceName.orEmpty())
        val matchScore = matchScore(target, text, desc, id)
        if (matchScore > 0) {
            val actionable = findActionableAncestor(node, longClick)
            val chosen = actionable ?: node
            val bounds = Rect()
            runCatching { chosen.getBoundsInScreen(bounds) }
            var score = matchScore
            if (chosen.isEnabled) score += 20
            if (!longClick && chosen.isClickable) score += 45
            if (longClick && chosen.isLongClickable) score += 55
            if (chosen.isEditable) score += 5
            if (actionable != null) score += 15
            out += Target(chosen, score, bounds)
        }
        for (i in 0 until node.childCount) runCatching { node.getChild(i)?.let { collect(it, target, longClick, out) } }
    }

    private fun findActionableAncestor(node: AccessibilityNodeInfo, longClick: Boolean): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth++ < 12) {
            if (current.isVisibleToUser && current.isEnabled && ((!longClick && current.isClickable) || (longClick && current.isLongClickable))) return current
            current = current.parent
        }
        return null
    }

    private fun matchScore(target: String, text: String, desc: String, id: String): Int {
        var best = 0
        fun match(value: String) {
            if (value.isBlank()) return
            if (value == target) best = maxOf(best, 110)
            else if (value.contains(target) || target.contains(value) && value.length >= 2) best = maxOf(best, 75)
        }
        match(text); match(desc); match(id)
        return best
    }

    fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9\\u00c0-\\u024f\\u0900-\\u097f\\u1c50-\\u1c7f]+"), " ")
        .trim().replace(Regex("\\s+"), " ")
}
