package com.ghaniram.zoya

import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/** Verifies that an Accessibility action produced an observable result before retrying it. */
object ActionVerifier {
    fun snapshot(service: AccessibilityControlService): JSONObject = runCatching {
        JSONObject(service.uiSnapshot())
    }.getOrElse { JSONObject().put("package", "").put("elements", JSONArray()) }

    fun changed(before: JSONObject, after: JSONObject): Boolean {
        return fingerprint(before) != fingerprint(after)
    }

    fun targetStateChanged(before: JSONObject, after: JSONObject, query: String): Boolean {
        val target = TargetResolver.normalize(query)
        if (target.isBlank()) return changed(before, after)
        val beforePresent = containsTarget(before, target)
        val afterPresent = containsTarget(after, target)
        return beforePresent != afterPresent || changed(before, after)
    }

    fun textApplied(service: AccessibilityControlService, query: String, value: String): Boolean {
        val target = TargetResolver.normalize(query)
        val wanted = value.trim()
        if (wanted.isBlank()) return false
        val root = service.rootNodeForVerification() ?: return false
        return findText(root, target, wanted)
    }

    fun focusedTextApplied(service: AccessibilityControlService, value: String): Boolean {
        val wanted = value.trim()
        if (wanted.isBlank()) return false
        val root = service.rootNodeForVerification() ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return focused?.text?.toString()?.contains(wanted, ignoreCase = false) == true
    }

    private fun findText(node: AccessibilityNodeInfo, target: String, wanted: String): Boolean {
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val matchesTarget = target.isBlank() || TargetResolver.normalize(text).contains(target) || TargetResolver.normalize(desc).contains(target)
        if (matchesTarget && text.contains(wanted)) return true
        for (i in 0 until node.childCount) {
            val found = runCatching { node.getChild(i)?.let { findText(it, target, wanted) } ?: false }.getOrDefault(false)
            if (found) return true
        }
        return false
    }

    private fun containsTarget(snapshot: JSONObject, target: String): Boolean {
        val elements = snapshot.optJSONArray("elements") ?: return false
        for (i in 0 until elements.length()) {
            val element = elements.optJSONObject(i) ?: continue
            val text = TargetResolver.normalize(element.optString("text"))
            val desc = TargetResolver.normalize(element.optString("contentDescription"))
            if (text == target || desc == target || text.contains(target) || desc.contains(target)) return true
        }
        return false
    }

    private fun fingerprint(snapshot: JSONObject): String {
        val elements = snapshot.optJSONArray("elements") ?: JSONArray()
        val parts = ArrayList<String>(minOf(elements.length(), 80))
        for (i in 0 until minOf(elements.length(), 80)) {
            val e = elements.optJSONObject(i) ?: continue
            parts += listOf(
                e.optString("text"),
                e.optString("contentDescription"),
                e.optString("viewId"),
                e.optBoolean("selected").toString(),
                e.optBoolean("scrollable").toString()
            ).joinToString("|")
        }
        return snapshot.optString("package") + "#" + parts.joinToString(";")
    }
}
