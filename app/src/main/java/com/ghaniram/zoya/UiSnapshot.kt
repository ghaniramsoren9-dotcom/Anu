package com.ghaniram.zoya

import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/** Compact, privacy-conscious representation of the visible Accessibility UI tree. */
data class UiElement(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val selected: Boolean,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int
)

object UiSnapshot {
    fun capture(root: AccessibilityNodeInfo?, packageName: String?): JSONObject {
        val elements = JSONArray()
        if (root != null) {
            val list = ArrayList<UiElement>()
            collect(root, list)
            list.take(180).forEach { e ->
                elements.put(JSONObject().apply {
                    put("index", e.index); put("text", e.text); put("contentDescription", e.contentDescription)
                    put("className", e.className); put("viewId", e.viewId); put("clickable", e.clickable)
                    put("editable", e.editable); put("scrollable", e.scrollable); put("enabled", e.enabled)
                    put("selected", e.selected)
                    put("bounds", JSONArray().put(e.boundsLeft).put(e.boundsTop).put(e.boundsRight).put(e.boundsBottom))
                })
            }
        }
        return JSONObject().put("package", packageName ?: "").put("elements", elements)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<UiElement>) {
        if (!node.isVisibleToUser || out.size >= 180) return
        val rect = android.graphics.Rect()
        runCatching { node.getBoundsInScreen(rect) }
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val id = runCatching { node.viewIdResourceName ?: "" }.getOrDefault("")
        if (text.isNotEmpty() || desc.isNotEmpty() || node.isClickable || node.isEditable || node.isScrollable) {
            out.add(UiElement(out.size, text.take(300), desc.take(300), node.className?.toString().orEmpty(), id.take(300), node.isClickable, node.isEditable, node.isScrollable, node.isEnabled, node.isSelected, rect.left, rect.top, rect.right, rect.bottom))
        }
        for (i in 0 until node.childCount) runCatching { node.getChild(i)?.let { collect(it, out) } }
    }
}
