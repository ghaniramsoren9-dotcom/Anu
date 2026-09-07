package com.ghaniram.zoya

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Keeps a user-requested Gemini Live session alive across the Live API's
 * periodic WebSocket connection rotation and transient disconnects.
 */
object LiveConnectionWatchdog {
    private const val CHECK_INTERVAL_MS = 2_000L
    private const val RECONNECT_COOLDOWN_MS = 8_000L

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var lastReconnectAt = 0L
    private lateinit var appContext: Context

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            runCatching {
                val prefs = appContext.getSharedPreferences("anu_session", Context.MODE_PRIVATE)
                val active = prefs.getBoolean("active", false)
                if (active) {
                    val state = ZoyaSessionManager.state.value.connectionState
                    if (state == ConnectionState.CONNECTING || state == ConnectionState.DISCONNECTED) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReconnectAt >= RECONNECT_COOLDOWN_MS) {
                            lastReconnectAt = now
                            ZoyaSessionManager.connect()
                        }
                    }
                }
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (running) return
        running = true
        lastReconnectAt = 0L
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}
