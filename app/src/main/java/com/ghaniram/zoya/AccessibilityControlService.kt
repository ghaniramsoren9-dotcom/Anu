package com.ghaniram.zoya

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** User-enabled AccessibilityService for UI controls, screen reading, autonomous help. */
class AccessibilityControlService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        TouchGuardRuntime.onAccessibilityEvent(this, event)
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val pkg = event.packageName?.toString()
                    ?: rootInActiveWindow?.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    ProactiveEventEngine.onForegroundAppChanged(applicationContext, pkg)
                }
            }
        }
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun currentPackageName(): String? = rootInActiveWindow?.packageName?.toString()
    fun rootNodeForVerification(): AccessibilityNodeInfo? = rootInActiveWindow
    fun uiSnapshot(): String = runCatching {
        UiSnapshot.capture(rootInActiveWindow, currentPackageName()).toString()
    }.getOrDefault("{\"package\":\"\",\"elements\":[]}")

    fun globalAction(action: String): Boolean = when (normalizeAction(action)) {
        "home", "gohome" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "back", "goback" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "recents", "recentapps", "openrecentapps" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications", "opennotifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quicksettings", "openquicksettings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "power", "powerdialog" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        "lock", "lockscreen", "lockphone" -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false
        else -> false
    }

    fun clickByText(text: String, longClick: Boolean = false): Boolean {
        if (text.isBlank()) return false
        // Also try common camera / selfie related labels automatically when the query is generic
        val variants = when {
            text.equals("shutter", true) || text.equals("capture", true) || text.equals("take photo", true) ||
            text.equals("selfie", true) || text.equals("photo", true) ->
                listOf(text, "Shutter", "Capture", "Take photo", "Photo", "Camera", "Snap", "Shoot", "OK", "Done")
            text.equals("flip", true) || text.equals("switch", true) || text.equals("front", true) ||
            text.equals("switch camera", true) ->
                listOf(text, "Flip", "Switch", "Front", "Rear", "Switch camera", "Cameraswitch", "Facing")
            else -> listOf(text)
        }
        for (candidate in variants) {
            repeat(2) { attempt ->
                val before = ActionVerifier.snapshot(this)
                val target = TargetResolver.resolve(rootInActiveWindow, candidate, longClick)
                if (target != null && target.node.isEnabled && target.node.isVisibleToUser) {
                    val acted = clickNodeOrAncestor(target.node, longClick) || performCoordinateGesture(target.node, longClick)
                    if (acted) {
                        waitForUiSettle(150L + attempt * 120L)
                        if (ActionVerifier.targetStateChanged(before, ActionVerifier.snapshot(this), candidate) ||
                            ActionVerifier.changed(before, ActionVerifier.snapshot(this))) return true
                    }
                }
                if (attempt < 1) waitForUiSettle(120L + attempt * 100L)
            }
        }
        return false
    }

    fun setTextByText(text: String, value: String): Boolean {
        if (text.isBlank()) return false
        repeat(3) { attempt ->
            val target = TargetResolver.resolve(rootInActiveWindow, text)
            if (target != null && target.node.isEnabled && target.node.isVisibleToUser) {
                if (setTextOnNode(target.node, value)) {
                    waitForUiSettle(120L + attempt * 80L)
                    if (ActionVerifier.textApplied(this, text, value)) return true
                }
            }
            if (attempt < 2) waitForUiSettle(120L + attempt * 80L)
        }
        return false
    }

    fun typeText(value: String): Boolean {
        if (value.isEmpty()) return false
        repeat(3) { attempt ->
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val target = focused ?: findEditable(root ?: return@repeat)
            val acted = target?.let { setTextOnNode(it, value) } ?: false
            if (acted) {
                waitForUiSettle(120L + attempt * 80L)
                if (ActionVerifier.focusedTextApplied(this, value)) {
                    val beforeSubmit = ActionVerifier.snapshot(this)
                    if (clickByAnyText(listOf("Search", "Go", "Enter", "Submit"))) {
                        waitForUiSettle(220L + attempt * 100L)
                        if (ActionVerifier.changed(beforeSubmit, ActionVerifier.snapshot(this))) return true
                    }
                    return true
                }
            }
            if (attempt < 2) waitForUiSettle(120L + attempt * 80L)
        }
        return false
    }

    private fun clickByAnyText(labels: List<String>): Boolean {
        for (label in labels) if (clickByText(label)) return true
        return false
    }

    private fun setTextOnNode(node: AccessibilityNodeInfo, value: String): Boolean = try {
        if (!node.isVisibleToUser || !node.isEnabled || !node.isEditable) false
        else node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        })
    } catch (_: Exception) { false }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && node.isEnabled && node.isEditable) return node
        for (i in 0 until node.childCount) try {
            val child = node.getChild(i) ?: continue
            findEditable(child)?.let { return it }
        } catch (_: Exception) { }
        return null
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo, longClick: Boolean): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth++ < 12) try {
            if (longClick && current.isLongClickable && current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
            if (!longClick && current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
        } catch (_: Exception) { break }
        return false
    }

    private fun performCoordinateGesture(node: AccessibilityNodeInfo, longClick: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val bounds = Rect()
        try { node.getBoundsInScreen(bounds) } catch (_: Exception) { return false }
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, if (longClick) 650L else 1L))
                .build(),
            null,
            null
        )
    }

    fun scroll(forward: Boolean): Boolean {
        repeat(3) { attempt ->
            val before = ActionVerifier.snapshot(this)
            val root = rootInActiveWindow ?: return@repeat
            val node = findScrollable(root)
            var acted = false
            if (node != null) {
                acted = runCatching {
                    node.performAction(
                        if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    )
                }.getOrDefault(false)
                if (!acted) acted = performScrollGesture(node, forward)
            }
            if (acted) {
                waitForUiSettle(180L + attempt * 120L)
                if (ActionVerifier.changed(before, ActionVerifier.snapshot(this))) return true
                if (node != null && attempt < 2) {
                    performScrollGesture(node, forward); waitForUiSettle(220L)
                    if (ActionVerifier.changed(before, ActionVerifier.snapshot(this))) return true
                }
            } else if (node != null && attempt < 2) {
                performScrollGesture(node, forward); waitForUiSettle(220L)
                if (ActionVerifier.changed(before, ActionVerifier.snapshot(this))) return true
            }
            if (attempt < 2) waitForUiSettle(120L + attempt * 100L)
        }
        return false
    }

    private fun performScrollGesture(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }.getOrElse { return false }
        if (bounds.isEmpty || bounds.height() < 80 || bounds.width() < 40) return false
        val x = bounds.exactCenterX()
        val startY = if (forward) bounds.bottom - bounds.height() * 0.25f else bounds.top + bounds.height() * 0.25f
        val endY = if (forward) bounds.top + bounds.height() * 0.25f else bounds.bottom - bounds.height() * 0.25f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 350L))
                .build(),
            null,
            null
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) try {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        } catch (_: Exception) { }
        return null
    }

    private fun waitForUiSettle(delayMs: Long) {
        try { Thread.sleep(delayMs.coerceAtMost(450L)) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun normalizeAction(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    companion object {
        @Volatile var instance: AccessibilityControlService? = null
            private set
    }
}
