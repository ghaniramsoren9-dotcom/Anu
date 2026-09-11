package com.ghaniram.zoya

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * Proactive speech uses ONLY Gemini Live audio — never Android system TTS.
 * If the session is off, reconnect so Anu can speak with her real voice.
 */
object ProactiveVoiceBridge {
    private const val TAG = "ProactiveVoiceBridge"

    fun dispatch(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val app = context.applicationContext as? Application ?: return
        runCatching {
            ZoyaSessionManager.initialize(app)
            if (ZoyaSessionManager.state.value.connectionState == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Connecting Live session for proactive Gemini voice")
                ZoyaSessionManager.connect()
            }
            // Send via ZoyaSessionManager.sendText so it goes through standard flow.
            // With pending message queue in GeminiLiveClient and clientContent turnComplete=true,
            // Gemini will immediately process and speak the response once connected.
            ZoyaSessionManager.sendText(prompt)
        }.onFailure {
            Log.w(TAG, "Proactive Gemini dispatch failed: ${it.message}")
        }
    }
}
