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
 * Autonomous runtime: system events + ambient screen awareness.
 * Anu can independently offer help while the user uses any app.
 */
object ProactiveEventEngine {
    private const val DEBOUNCE_MS = 1_500L
    private const val SCREEN_CHECK_MS = 45_000L
    private const val APP_SWITCH_COOLDOWN_MS = 25_000L
    private const val PROACTIVE_COOLDOWN_MS = 90_000L
    private const val IDLE_NUDGE_MS = 360_000L
    private val lastDispatch = ConcurrentHashMap<String, AtomicLong>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ambientRunning = false
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkManager: ConnectivityManager? = null
    @Volatile private var lastAmbientSnapshot = ""
    @Volatile private var lastAmbientSpokenAt = 0L
    @Volatile private var lastUserActivityAt = SystemClock.elapsedRealtime()
    @Volatile private var lastPackage = ""

    fun dispatch(context: Context, event: String, key: String = event) {
        if (event.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return
        ProactiveVoiceBridge.dispatch(
            context,
            "[PROACTIVE SYSTEM EVENT] $event\n" +
                "Speak to the user proactively in one short, natural sentence. " +
                "Use judgment: speak only when genuinely useful. Do not narrate the whole screen. " +
                "Do not mention this system event label."
        )
    }

    fun startSystemEventMonitoring(context: Context) = startNetworkMonitor(context.applicationContext)

    /**
     * Called from AccessibilityService when the foreground app/window changes.
     * Lets Anu offer contextual help while the user is using any app.
     */
    fun onForegroundAppChanged(context: Context, packageName: String?) {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank() || pkg == lastPackage) return
        lastPackage = pkg
        noteUserActivity()
        val store = runCatching { AnuSettingsStore.getInstance(context) }.getOrNull() ?: return
        if (!store.proactiveAnu) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAmbientSpokenAt < APP_SWITCH_COOLDOWN_MS) return

        val snapshot = AccessibilityControlService.instance?.uiSnapshot().orEmpty()
        if (snapshot.isBlank() || snapshot == "{\"package\":\"\",\"elements\":[]}") return

        lastAmbientSnapshot = snapshot
        lastAmbientSpokenAt = now
        dispatch(
            context,
            "The user just opened or switched to app package `$pkg`. " +
                "Independently decide if a short, useful tip, warning, or suggestion helps right now. " +
                "If nothing useful, stay silent. UI snapshot: ${snapshot.take(10000)}",
            key = "app-switch:$pkg"
        )
    }

    fun startAmbientScreenAwareness(context: Context) {
        val app = context.applicationContext
        if (ambientRunning) return
        ambientRunning = true
        startNetworkMonitor(app)
        val tick = object : Runnable {
            override fun run() {
                if (!ambientRunning) return
                runCatching {
                    val store = AnuSettingsStore.getInstance(app)
                    if (store.proactiveAnu) {
                        val snapshot = AccessibilityControlService.instance?.uiSnapshot().orEmpty()
                        val valid = snapshot.isNotBlank() && snapshot != "{\"package\":\"\",\"elements\":[]}"
                        val now = SystemClock.elapsedRealtime()
                        val changed = valid && snapshot != lastAmbientSnapshot
                        val quietEnough = now - lastAmbientSpokenAt >= PROACTIVE_COOLDOWN_MS
                        if (changed && quietEnough) {
                            lastAmbientSnapshot = snapshot
                            lastAmbientSpokenAt = now
                            dispatch(
                                app,
                                "The user's screen content changed. Independently offer a short useful tip or warning only if warranted. " +
                                    "Stay silent if nothing important. UI snapshot: ${snapshot.take(12000)}",
                                "ambient-screen"
                            )
                        } else if (now - lastUserActivityAt >= IDLE_NUDGE_MS &&
                            now - lastAmbientSpokenAt >= PROACTIVE_COOLDOWN_MS
                        ) {
                            lastAmbientSpokenAt = now
                            dispatch(
                                app,
                                "The user has been quiet for several minutes. " +
                                    "Independently decide whether a brief helpful check-in is appropriate. If not, remain silent.",
                                "ambient-idle"
                            )
                        }
                    }
                }
                handler.postDelayed(this, SCREEN_CHECK_MS)
            }
        }
        handler.postDelayed(tick, 8_000L)
    }

    fun noteUserActivity() {
        lastUserActivityAt = SystemClock.elapsedRealtime()
    }

    private fun startNetworkMonitor(context: Context) {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var currentTransport = -1
            private var initialized = false

            override fun onAvailable(network: Network) {}

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCapabilities.TRANSPORT_CELLULAR
                    else -> -1
                }
                if (transport == -1) return
                if (!initialized) {
                    initialized = true
                    currentTransport = transport
                    return
                }
                if (transport == currentTransport) return
                val store = runCatching { AnuSettingsStore.getInstance(context) }.getOrNull() ?: return
                if (!store.eventAnnouncementsMaster) return
                if (currentTransport == NetworkCapabilities.TRANSPORT_WIFI && store.triggerWifiLost) {
                    dispatch(context, "Wi-Fi connectivity was lost.", "wifi:lost")
                } else if (currentTransport == NetworkCapabilities.TRANSPORT_CELLULAR) {
                    dispatch(context, "Mobile data connectivity was lost.", "data:lost")
                }
                currentTransport = transport
                if (transport == NetworkCapabilities.TRANSPORT_WIFI && store.triggerWifiConnected) {
                    dispatch(context, "Wi-Fi connected.", "wifi:connected")
                } else if (transport == NetworkCapabilities.TRANSPORT_CELLULAR) {
                    dispatch(context, "Mobile data connected.", "data:connected")
                }
            }

            override fun onLost(network: Network) {
                val store = runCatching { AnuSettingsStore.getInstance(context) }.getOrNull() ?: return
                if (!store.eventAnnouncementsMaster) return
                when (currentTransport) {
                    NetworkCapabilities.TRANSPORT_WIFI ->
                        if (store.triggerWifiLost) dispatch(context, "Wi-Fi connectivity was lost.", "wifi:lost")
                    NetworkCapabilities.TRANSPORT_CELLULAR ->
                        dispatch(context, "Mobile data connectivity was lost.", "data:lost")
                }
                currentTransport = -1
            }
        }
        runCatching {
            cm.registerDefaultNetworkCallback(callback)
            networkManager = cm
            networkCallback = callback
        }
    }

    fun stopAmbientScreenAwareness() {
        ambientRunning = false
        handler.removeCallbacksAndMessages(null)
        val cm = networkManager
        val callback = networkCallback
        if (cm != null && callback != null) runCatching { cm.unregisterNetworkCallback(callback) }
        networkCallback = null
        networkManager = null
        lastAmbientSnapshot = ""
        lastPackage = ""
    }
}
