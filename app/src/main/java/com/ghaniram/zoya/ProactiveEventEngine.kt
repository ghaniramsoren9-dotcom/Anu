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

/** Single runtime bridge for Android events and ambient screen awareness -> Anu Live. */
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
        val app = context.applicationContext as android.app.Application
        val now = SystemClock.elapsedRealtime()
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return

        ZoyaSessionManager.initialize(app)
        if (ZoyaSessionManager.state.value.connectionState == ConnectionState.DISCONNECTED) {
            ZoyaSessionManager.connect()
        }
        ZoyaSessionManager.sendText(
            "[PROACTIVE SYSTEM EVENT] $event\n" +
                "Speak to the user proactively in one short, natural sentence. " +
                "Do not claim an action was performed; this is only an event notification."
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
                        if (snapshot.isNotBlank() && snapshot != "{\"package\":\"\",\"elements\":[]}") {
                            val compact = snapshot.take(12000)
                            dispatch(
                                app,
                                "The user's current screen was observed by Anu's screen-awareness layer. " +
                                    "Review this UI snapshot and speak only if you can offer genuinely useful help, " +
                                    "a warning, a relevant suggestion, or a concise observation. Never narrate the whole screen. " +
                                    "UI snapshot: $compact",
                                key = "ambient-screen"
                            )
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
            private var hasNetwork = false
            override fun onAvailable(network: Network) {
                if (!hasNetwork) {
                    hasNetwork = true
                    runCatching {
                        val store = AnuSettingsStore.getInstance(context)
                        if (store.proactiveAnu && store.triggerWifiConnected) dispatch(context, "Network connectivity was restored.", "network:available")
                    }
                }
            }
            override fun onLost(network: Network) {
                hasNetwork = false
                runCatching {
                    val store = AnuSettingsStore.getInstance(context)
                    if (store.proactiveAnu && store.triggerWifiLost) dispatch(context, "Network connectivity was lost.", "network:lost")
                }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = Unit
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
