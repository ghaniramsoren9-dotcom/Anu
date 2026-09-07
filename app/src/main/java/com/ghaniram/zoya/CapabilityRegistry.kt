package com.ghaniram.zoya

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Single source of truth for Anu capability health.
 *
 * This class deliberately does not execute actions. Existing executors remain
 * the owners of Android side effects; this registry answers one question:
 * "Is this capability configured and ready to execute right now?"
 */
object CapabilityRegistry {
    enum class State { TRUE, PARTIAL, UI_ONLY, DISABLED }

    data class Status(
        val id: String,
        val enabled: Boolean,
        val ready: Boolean,
        val state: State,
        val reason: String
    )

    private val statuses = linkedMapOf<String, Status>()

    @Synchronized
    fun refresh(context: Context): Map<String, Status> {
        val app = context.applicationContext
        val store = AnuSettingsStore.getInstance(app)
        val connected = ZoyaSessionManager.state.value.connectionState != ConnectionState.DISCONNECTED

        put("agent", "Agent Mode", true, connected,
            if (connected) State.TRUE else State.PARTIAL,
            if (connected) "Live agent session is connected" else "Agent runtime is idle/disconnected")

        val accessibility = isAccessibilityEnabled(app)
        put("accessibility", "Accessibility", accessibility, accessibility,
            if (accessibility) State.TRUE else State.DISABLED,
            if (accessibility) "AccessibilityService is enabled" else "Enable Anu Accessibility Service")

        val screenReady = accessibility
        put("screen_awareness", "Screen Awareness", store.proactiveAnu, screenReady,
            if (!store.proactiveAnu) State.DISABLED else if (screenReady) State.TRUE else State.PARTIAL,
            when {
                !store.proactiveAnu -> "Screen Awareness is OFF"
                screenReady -> "Accessibility UI snapshot is available"
                else -> "Screen Awareness requires Accessibility Service"
            })

        val camera = hasPermission(app, Manifest.permission.CAMERA)
        put("vision", "Camera Vision", camera, camera,
            if (camera) State.TRUE else State.DISABLED,
            if (camera) "Camera permission is granted" else "Camera permission is missing")

        val microphone = hasPermission(app, Manifest.permission.RECORD_AUDIO)
        put("voice", "Live Voice", true, microphone && store.customGeminiKey.isNotBlank(),
            if (microphone && store.customGeminiKey.isNotBlank()) State.TRUE else State.PARTIAL,
            when {
                !microphone -> "Microphone permission is missing"
                store.customGeminiKey.isBlank() -> "Gemini API key is not configured"
                else -> "Voice runtime prerequisites are ready"
            })

        val wakeEnabled = store.bringWakeWordBack
        put("wake_word", "Wake Word", wakeEnabled, wakeEnabled && microphone,
            if (!wakeEnabled) State.DISABLED else if (microphone) State.TRUE else State.PARTIAL,
            when {
                !wakeEnabled -> "Wake Word is OFF"
                microphone -> "Wake-word runtime is enabled"
                else -> "Wake Word requires microphone permission"
            })

        put("proactive", "Proactive Anu", store.proactiveAnu, store.proactiveAnu,
            if (store.proactiveAnu) State.TRUE else State.DISABLED,
            if (store.proactiveAnu) "Proactive runtime is enabled" else "Proactive Anu is OFF")

        val notifications = isNotificationListenerEnabled(app)
        put("notifications", "Notification Automation", notifications, notifications,
            if (notifications) State.TRUE else State.PARTIAL,
            if (notifications) "Notification listener is enabled" else "Enable Notification Access")

        val overlay = Settings.canDrawOverlays(app)
        put("overlay", "Floating Overlay", overlay, overlay,
            if (overlay) State.TRUE else State.PARTIAL,
            if (overlay) "Overlay permission is granted" else "Overlay permission is not granted")

        val writeSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(app) else true
        put("system_settings", "Modify System Settings", writeSettings, writeSettings,
            if (writeSettings) State.TRUE else State.PARTIAL,
            if (writeSettings) "System settings write access is available" else "Grant Modify System Settings")

        put("planner", "Autonomous Task Planner", true, true, State.PARTIAL,
            "Planner creates steps; ZoyaSessionManager remains the execution owner")

        put("whatsapp", "WhatsApp Automation", true, notifications && accessibility, State.PARTIAL,
            if (notifications && accessibility) "Notification + Accessibility infrastructure is ready" else "Requires Notification Access and Accessibility")

        val emailConfigured = store.emailAddress.isNotBlank() && store.emailAppPassword.isNotBlank()
        put("email", "Email Automation", emailConfigured, false, State.PARTIAL,
            if (emailConfigured) "Email configuration exists; runtime delivery still needs verification" else "Email account/app password is not configured")

        return statuses.toMap()
    }

    @Synchronized
    fun snapshot(context: Context): Map<String, Status> = refresh(context)

    private fun put(id: String, name: String, enabled: Boolean, ready: Boolean, state: State, reason: String) {
        statuses[id] = Status(id, enabled, ready, state, "$name: $reason")
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        return enabled.contains(context.packageName)
    }
}
