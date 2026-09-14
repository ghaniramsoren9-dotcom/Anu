package com.ghaniram.zoya

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** User-enabled AccessibilityService for UI controls, screen reading, autonomous help. */
class AccessibilityControlService : AccessibilityService() {
    private val screenshotExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Anu-Screenshot").apply { isDaemon = true }
    }

    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        TouchGuardRuntime.onAccessibilityEvent(this, event)
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AutonomousScreenHelpManager.onWindowStateChanged(this, event.packageName?.toString().orEmpty())
        }
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        screenshotExecutor.shutdownNow()
        if (instance === this) instance = null
        super.onDestroy()
    }
    fun currentPackageName(): String? = rootInActiveWindow?.packageName?.toString()
    fun rootNodeForVerification(): AccessibilityNodeInfo? = rootInActiveWindow
    fun uiSnapshot(): String = runCatching { UiSnapshot.capture(rootInActiveWindow, currentPackageName()).toString() }.getOrDefault("{\"package\":\"\",\"elements\":[]}")

    fun captureScreenVisionFrame(onCaptured: (ByteArray) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) {
            onCaptured(ByteArray(0))
            return
        }
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bytes = runCatching {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val original = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                            hardwareBuffer.close()
                            if (original == null) ByteArray(0) else {
                                val maxDimension = 768
                                val scale = minOf(1f, maxDimension.toFloat() / maxOf(original.width, original.height))
                                val bitmap = if (scale < 1f) {
                                    Bitmap.createScaledBitmap(
                                        original,
                                        (original.width * scale).toInt().coerceAtLeast(1),
                                        (original.height * scale).toInt().coerceAtLeast(1),
                                        true
                                    ).also { if (it !== original) original.recycle() }
                                } else original
                                ByteArrayOutputStream().use { out ->
                                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        .compress(Bitmap.CompressFormat.JPEG, 45, out)
                                    bitmap.recycle()
                                    out.toByteArray()
                                }
                            }
                        }.getOrDefault(ByteArray(0))
                        onCaptured(bytes)
                    }
                    override fun onFailure(errorCode: Int) {
                        onCaptured(ByteArray(0))
                    }
                }
            )
        }.onFailure { onCaptured(ByteArray(0)) }
    }

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
        repeat(3) { attempt ->
            val before = ActionVerifier.snapshot(this)
            val target = TargetResolver.resolve(rootInActiveWindow, text, longClick)
            if (target != null && target.node.isEnabled && target.node.isVisibleToUser) {
                val acted = clickNodeOrAncestor(target.node, longClick) || performCoordinateGesture(target.node, longClick)
                if (acted) {
                    waitForUiSettle(120L + attempt * 100L)
                    if (ActionVerifier.targetStateChanged(before, ActionVerifier.snapshot(this), text)) return true
                }
            }
            if (attempt < 2) waitForUiSettle(120L + attempt * 100L)
        }
        return false
    }

    fun setTextByText(text: String, value: String): Boolean {
        if (text.isBlank()) return false
        repeat(3) { attempt ->
            val target = TargetResolver.resolve(rootInActiveWindow, text)
            if (target != null && target.node.isEnabled && target.node.isVisibleToUser) {
                var acted = false
                if (value.length > 60 || value.contains('\n')) {
                    acted = pasteTextToNode(target.node, value)
                }
                if (!acted) {
                    acted = setTextOnNode(target.node, value)
                }
                if (!acted) {
                    acted = pasteTextToNode(target.node, value)
                }
                if (acted) {
                    waitForUiSettle(120L + attempt * 80L)
                    if (ActionVerifier.textApplied(this, text, value)) return true
                    return true
                }
            }
            if (attempt < 2) waitForUiSettle(120L + attempt * 80L)
        }
        return false
    }

    /**
     * Type text reliably without auto-submitting or losing focus.
     * Supports long code snippets, multi-line notes, and text of any length
     * using hybrid clipboard paste and direct text actions.
     */
    fun typeText(value: String): Boolean {
        if (value.isEmpty()) return false
        repeat(3) { attempt ->
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val target = focused ?: findEditable(root ?: return@repeat)
            if (target != null) {
                var acted = false
                // For long text (> 60 chars or containing newlines like notes or code), paste is fastest & lossless
                if (value.length > 60 || value.contains('\n')) {
                    acted = pasteTextToNode(target, value)
                }
                if (!acted) {
                    acted = setTextOnNode(target, value)
                }
                if (!acted) {
                    acted = pasteTextToNode(target, value)
                }
                if (acted) {
                    waitForUiSettle(120L + attempt * 80L)
                    return true
                }
            }
            if (attempt < 2) waitForUiSettle(120L + attempt * 80L)
        }
        return false
    }

    fun pasteTextToNode(node: AccessibilityNodeInfo, value: String): Boolean = try {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Anu text", value)
        clipboard?.setPrimaryClip(clip)
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    } catch (_: Exception) { false }

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
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, if (longClick) 650L else 1L)).build(), null, null)
    }

    fun scroll(forward: Boolean): Boolean {
        val before = ActionVerifier.snapshot(this)
        val root = rootInActiveWindow
        val node = if (root != null) findScrollable(root) else null
        var acted = false
        if (node != null) {
            acted = runCatching {
                node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }.getOrDefault(false)
            if (!acted) acted = performScrollGesture(node, forward)
        }
        if (!acted) acted = performScreenScrollGesture(forward)
        waitForUiSettle(180L)
        return acted || ActionVerifier.changed(before, ActionVerifier.snapshot(this))
    }

    private fun performScreenScrollGesture(forward: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val dm = resources.displayMetrics
        val width = dm.widthPixels.toFloat()
        val height = dm.heightPixels.toFloat()
        val x = width * 0.5f
        val startY = if (forward) height * 0.75f else height * 0.25f
        val endY = if (forward) height * 0.25f else height * 0.75f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 300L)).build(), null, null)
    }

    private fun performScrollGesture(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val bounds = Rect(); runCatching { node.getBoundsInScreen(bounds) }.getOrElse { return false }
        if (bounds.isEmpty || bounds.height() < 80 || bounds.width() < 40) return false
        val x = bounds.exactCenterX()
        val startY = if (forward) bounds.bottom - bounds.height() * 0.25f else bounds.top + bounds.height() * 0.25f
        val endY = if (forward) bounds.top + bounds.height() * 0.25f else bounds.bottom - bounds.height() * 0.25f
        val path = Path().apply { moveTo(x, startY); lineTo(x, endY) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 350L)).build(), null, null)
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) try {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        } catch (_: Exception) { }
        return null
    }

    private fun waitForUiSettle(delayMs: Long) { try { Thread.sleep(delayMs.coerceAtMost(450L)) } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
    private fun normalizeAction(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")

    companion object {
        @Volatile var instance: AccessibilityControlService? = null
            private set
    }
}
