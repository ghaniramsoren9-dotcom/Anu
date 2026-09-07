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

/** Single runtime bridge for Android events and ambient screen awareness -> Anu voice. */
object ProactiveEventEngine {
    private const val DEBOUNCE_MS = 2_000L
    private const val SCREEN_CHECK_MS = 30_000L
    private val lastDispatch = ConcurrentHashMap<String, AtomicLong>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ambientRunning = false
    @Volatile private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var networkManager: ConnectivityManager? = null

    fun dispatch(context: Context, event: String, key: String = event) {
        if (event.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return
        ProactiveVoiceBridge.dispatch(context, "[PROACTIVE SYSTEM EVENT] $event\n" +
            "Speak to the user proactively in one short, natural sentence. " +
            "Do not claim an action was performed; this is only an event notification. " +
            "Do not mention or display the event payload itself.")
    }

    /** Event monitoring is independent from Proactive Anu/screen awareness. */
    fun startSystemEventMonitoring(context: Context) = startNetworkMonitor(context.applicationContext)

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
                        if (snapshot.isNotBlank() && snapshot != "{\"package\":\"\",\"elements\":[]}") {
                            dispatch(app, "The user's current screen was observed by Anu's screen-awareness layer. Review this UI snapshot and speak only if you can offer genuinely useful help, a warning, a relevant suggestion, or a concise observation. Never narrate the whole screen. UI snapshot: ${snapshot.take(12000)}", "ambient-screen")
                        }
                    }
                }
                handler.postDelayed(this, SCREEN_CHECK_MS)
            }
        }
        handler.post(tick)
    }

    private fun startNetworkMonitor(context: Context) {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var currentTransport = -1
            private var initialized = false

            override fun onAvailable(network: Network) {
                // Wait for onCapabilitiesChanged so Wi-Fi vs mobile is known before announcing.
            }

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
    }
}
