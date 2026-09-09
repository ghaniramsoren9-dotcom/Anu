package com.ghaniram.zoya

import android.app.Application
import android.content.Context
import android.util.Log

/** Single proactive speech path: Gemini Live only, never Android system TTS. */
object ProactiveVoiceBridge {
    private const val TAG = "ProactiveVoiceBridge"
    fun dispatch(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        runCatching {
            val app = context.applicationContext as Application
            ZoyaSessionManager.initialize(app)
            ZoyaSessionManager.sendText("[PROACTIVE SYSTEM EVENT] $prompt\nSpeak this event now through the configured Gemini Live voice. Do not use Android system TTS. If this is a reminder, announce it immediately.")
        }.onFailure { Log.w(TAG, "Proactive Gemini dispatch failed: ${it.message}") }
    }
}
