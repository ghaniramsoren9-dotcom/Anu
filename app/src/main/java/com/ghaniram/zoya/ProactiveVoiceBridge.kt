package com.ghaniram.zoya

import android.app.Application
import android.content.Context
import android.util.Log

/**
 * Proactive speech uses ONLY Gemini Live audio — never Android system TTS.
 * If the session is off, connects in playback-only mode so Anu speaks her reminder
 * without enabling or opening the microphone.
 */
object ProactiveVoiceBridge {
    private const val TAG = "ProactiveVoiceBridge"

    fun dispatch(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val app = context.applicationContext as? Application ?: return
        runCatching {
            ZoyaSessionManager.initialize(app)
            if (ZoyaSessionManager.state.value.connectionState == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Connecting Live session for proactive playback only (mic off)")
                ZoyaSessionManager.connectForProactive(prompt)
            } else {
                ZoyaSessionManager.sendText(prompt)
            }
        }.onFailure {
            Log.w(TAG, "Proactive Gemini dispatch failed: ${it.message}")
        }
    }
}
