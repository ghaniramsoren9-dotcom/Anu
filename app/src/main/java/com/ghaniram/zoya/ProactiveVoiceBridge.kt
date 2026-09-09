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
            // Prefer direct client send to avoid chat clutter when possible
            val sentDirect = runCatching {
                val field = ZoyaSessionManager::class.java.getDeclaredField("client")
                field.isAccessible = true
                val client = field.get(ZoyaSessionManager) as? GeminiLiveClient
                if (client != null) {
                    client.sendText(prompt)
                    true
                } else false
            }.getOrDefault(false)
            if (!sentDirect) {
                ZoyaSessionManager.sendText(prompt)
            }
        }.onFailure {
            Log.w(TAG, "Proactive Gemini dispatch failed: ${it.message}")
        }
    }
}
