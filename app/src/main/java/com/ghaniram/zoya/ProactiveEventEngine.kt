package com.ghaniram.zoya

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single runtime bridge for Android system events -> the existing Anu Live session.
 * It deliberately does not create a second Gemini client: events are queued into the
 * same GeminiLiveClient used by normal Anu voice conversations.
 */
object ProactiveEventEngine {
    private const val DEBOUNCE_MS = 2_000L
    private val lastDispatch = ConcurrentHashMap<String, AtomicLong>()

    fun dispatch(context: Context, event: String, key: String = event) {
        if (event.isBlank()) return
        val app = context.applicationContext as android.app.Application
        val now = SystemClock.elapsedRealtime()
        val stamp = lastDispatch.getOrPut(key) { AtomicLong(0L) }
        val previous = stamp.get()
        if (now - previous < DEBOUNCE_MS || !stamp.compareAndSet(previous, now)) return

        ZoyaSessionManager.initialize(app)
        // GeminiLiveClient already has an outbound queue while setup is in progress,
        // so do not block a BroadcastReceiver waiting for a WebSocket handshake.
        if (ZoyaSessionManager.state.value.connectionState == ConnectionState.DISCONNECTED) {
            ZoyaSessionManager.connect()
        }
        ZoyaSessionManager.sendText(
            "[PROACTIVE SYSTEM EVENT] $event\n" +
                "Respond to the user proactively in one short, natural sentence. " +
                "Do not claim to have performed any action; this is only an event notification."
        )
    }
}
