package com.ghaniram.zoya

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Autonomous runtime with NO artificial cooldown blocking independent speech.
 * Only a tiny debounce prevents identical duplicate floods in the same second.
 */
object ProactiveEventEngine {
    private const val DEBOUNCE_MS = 800L
    private const val SCREEN_CHECK_MS = 20_000L
    private const val IDLE_NUDGE_MS = 180_000L
    private val lastDispatch = ConcurrentHashMap<String, AtomicLong>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ambientRunning = false
    @Volatile private var lastAmbientSnapshot = ""
    @Volatile private var lastAmbientSpokenAt = 0L
    @Volatile private var lastUserActivityAt = SystemClock.elapsedRealtime()
    @Volatile private var lastPackage = ""
    @Volatile private var currentTransport = -1

    fun dispatch(context: Context, event: String, key: String = event) {
        val now = SystemClock.elapsedRealtime()
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        // Only block exact duplicate within ~0.8s — no long cooldown
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return
        ProactiveVoiceBridge.dispatch(
            context,
            "[PROACTIVE SYSTEM EVENT] $event\n" +
                "Speak to the user proactively in one short, natural sentence when useful. " +
                "If nothing useful, stay silent. Do not narrate the whole screen."
        )
    }

    fun dispatchReminder(context: Context, title: String, timeLabel: String, taskId: String) {
        val now = SystemClock.elapsedRealtime()
        val key = "reminder:${taskId.ifBlank { title }}"
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return
        val timeInfo = if (timeLabel.isNotBlank()) " at $timeLabel" else ""
        ProactiveVoiceBridge.dispatch(
            context,
            "[PROACTIVE SYSTEM EVENT] SPECIFIC REMINDER ALERT: It is time for \"$title\"$timeInfo.\n" +
                "STRICT INSTRUCTION: Speak ONLY about this single reminder \"$title\". " +
                "Do NOT mention, list, or speak about any other tasks or reminders from the screen or memory. " +
                "Say one short, warm sentence in the user's language reminding them to do \"$title\" now."
        )
    }

    fun startSystemEventMonitoring(context: Context) = startNetworkMonitor(context.applicationContext)

    fun onForegroundAppChanged(context: Context, packageName: String?) {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank() || pkg == lastPackage) return
        lastPackage = pkg
        noteUserActivity()
        val store = runCatching { AnuSettingsStore.getInstance(context) }.getOrNull() ?: return
        if (!store.proactiveAnu) return

        val snapshot = AccessibilityControlService.instance?.uiSnapshot().orEmpty()
        if (snapshot.isBlank() || snapshot == "{\"package\":\"\",\"elements\":[]}") return

        lastAmbientSnapshot = snapshot
        lastAmbientSpokenAt = SystemClock.elapsedRealtime()
        dispatch(
            context,
            "The user switched to app `$pkg`. Offer a short useful tip only if warranted. " +
                "UI snapshot: ${snapshot.take(10000)}",
            key = "app-switch:$pkg"
        )
    }

    fun startAmbientScreenAwareness(context: Context) {
        val app = context.applicationContext
        val store = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull() ?: return
        if (!store.proactiveAnu || ambientRunning) return
        ambientRunning = true

        val tick = object : Runnable {
            override fun run() {
                val currentStore = runCatching { AnuSettingsStore.getInstance(app) }.getOrNull()
                if (currentStore?.proactiveAnu == true) {
                    runCatching {
                        val snapshot = AccessibilityControlService.instance?.uiSnapshot().orEmpty()
                        val valid = snapshot.isNotBlank() && snapshot != "{\"package\":\"\",\"elements\":[]}"
                        val now = SystemClock.elapsedRealtime()
                        val changed = valid && snapshot != lastAmbientSnapshot
                        if (changed) {
                            lastAmbientSnapshot = snapshot
                            lastAmbientSpokenAt = now
                            dispatch(
                                app,
                                "Screen content changed. Offer a short tip only if useful. " +
                                    "UI snapshot: ${snapshot.take(12000)}",
                                "ambient-screen:${snapshot.hashCode()}"
                            )
                        } else if (now - lastUserActivityAt >= IDLE_NUDGE_MS &&
                            now - lastAmbientSpokenAt >= IDLE_NUDGE_MS
                        ) {
                            lastAmbientSpokenAt = now
                            dispatch(
                                app,
                                "User has been quiet. Brief helpful check-in only if appropriate; otherwise silent.",
                                "ambient-idle"
                            )
                        }
                    }
                }
                handler.postDelayed(this, SCREEN_CHECK_MS)
            }
        }
        handler.postDelayed(tick, 5_000L)
    }

    fun noteUserActivity() {
        lastUserActivityAt = SystemClock.elapsedRealtime()
    }

    private fun startNetworkMonitor(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val store = runCatching { AnuSettingsStore.getInstance(context) }.getOrNull() ?: return
                val transport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCapabilities.TRANSPORT_CELLULAR
                    else -> -1
                }
                if (transport == currentTransport) return
                if (!store.eventAnnouncementsMaster) return
                if (currentTransport == NetworkCapabilities.TRANSPORT_WIFI && store.triggerWifiLost) {
                    dispatch(context, "Wi-Fi connectivity was lost.", "wifi:lost")
                }
                currentTransport = transport
                if (transport == NetworkCapabilities.TRANSPORT_WIFI && store.triggerWifiConnected) {
                    dispatch(context, "Wi-Fi connected.", "wifi:connected")
                }
            }

            override fun onLost(network: Network) {
                val store = runCatching { AnuSettingsStore.getInstance(context) }.getOrNull() ?: return
                if (!store.eventAnnouncementsMaster) return
                if (currentTransport == NetworkCapabilities.TRANSPORT_WIFI && store.triggerWifiLost) {
                    dispatch(context, "Wi-Fi connectivity was lost.", "wifi:lost")
                }
                currentTransport = -1
            }
        })
    }
}
