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

/** Autonomous runtime bridge for Android events and contextual screen awareness. */
object ProactiveEventEngine {
    private const val DEBOUNCE_MS = 2_000L
    private const val SCREEN_CHECK_MS = 120_000L
    private const val PROACTIVE_COOLDOWN_MS = 300_000L
    private const val IDLE_NUDGE_MS = 420_000L
    private val lastDispatch = ConcurrentHashMap<String, AtomicLong>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ambientRunning = false
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkManager: ConnectivityManager? = null
    @Volatile private var lastAmbientSnapshot = ""
    @Volatile private var lastAmbientSpokenAt = 0L
    @Volatile private var lastUserActivityAt = SystemClock.elapsedRealtime()

    fun dispatch(context: Context, event: String, key: String = event) {
        if (event.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return
        ProactiveVoiceBridge.dispatch(context, "[PROACTIVE SYSTEM EVENT] $event\n" +
            "Speak to the user proactively in one short, natural sentence. " +
            "Use your own judgment: speak only when this is genuinely useful, relevant, time-sensitive, or helpful. " +
            "Do not narrate the whole screen. Do not mention or display the event payload itself.")
    }

    /** Event monitoring is independent from screen awareness and Proactive Anu. */
    fun startSystemEventMonitoring(context: Context) = startNetworkMonitor(context.applicationContext)

    /**
     * Autonomous screen awareness: sample less frequently, require meaningful screen change,
     * and enforce a quiet period so Anu does not read the screen aloud every 30 seconds.
     * Anu may still initiate a useful idle nudge when the user has been quiet for a while.
     */
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
                            dispatch(app,
                                "The user's current screen changed. Independently decide whether there is genuinely useful help, a warning, a relevant suggestion, or a concise observation to offer. Speak only if warranted. UI snapshot: ${snapshot.take(12000)}",
                                "ambient-screen")
                        } else if (now - lastUserActivityAt >= IDLE_NUDGE_MS && now - lastAmbientSpokenAt >= PROACTIVE_COOLDOWN_MS) {
                            lastAmbientSpokenAt = now
                            dispatch(app,
                                "The user has been quiet for several minutes. Independently decide whether a brief helpful check-in is appropriate right now. If not, remain silent.",
                                "ambient-idle")
                        }
                    }
                }
                handler.postDelayed(this, SCREEN_CHECK_MS)
            }
        }
        handler.post(tick)
    }

    /** Call this whenever the user speaks or sends a message to reset the autonomous idle timer. */
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
                    NetworkCapabilities.TRANSPORT_WIFI -> if (store.triggerWifiLost) dispatch(context, "Wi-Fi connectivity was lost.", "wifi:lost")
                    NetworkCapabilities.TRANSPORT_CELLULAR -> dispatch(context, "Mobile data connectivity was lost.", "data:lost")
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
    }
}
