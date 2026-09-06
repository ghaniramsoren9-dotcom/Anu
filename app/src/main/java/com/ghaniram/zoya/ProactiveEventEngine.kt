package com.ghaniram.zoya

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single runtime bridge for Android system events and ambient screen awareness -> Anu Live.
 * No second Gemini client is created; everything enters the existing Anu session.
 */
object ProactiveEventEngine {
    private const val DEBOUNCE_MS = 2_000L
    private const val SCREEN_CHECK_MS = 30_000L
    private val lastDispatch = ConcurrentHashMap<String, AtomicLong>()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var ambientRunning = false

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

    /**
     * Periodically gives Anu the current Accessibility UI snapshot so it can notice
     * useful things on screen even when the user is not speaking.
     */
    fun startAmbientScreenAwareness(context: Context) {
        val app = context.applicationContext
        if (ambientRunning) return
        ambientRunning = true
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

    fun stopAmbientScreenAwareness() {
        ambientRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
