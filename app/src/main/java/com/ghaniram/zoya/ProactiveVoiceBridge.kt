package com.ghaniram.zoya

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Sends proactive speech without changing the user's microphone preference.
 *
 * If the user has an active Live session, the event is spoken by Gemini. If the
 * user has intentionally turned the microphone/session off, we do NOT reconnect
 * the Live session (which would reopen the microphone); instead Android TTS
 * announces the event without enabling microphone capture.
 */
object ProactiveVoiceBridge {
    fun dispatch(context: Context, prompt: String) {
        if (prompt.isBlank()) return
        val app = context.applicationContext as? Application ?: return
        ZoyaSessionManager.initialize(app)

        if (ZoyaSessionManager.state.value.connectionState != ConnectionState.DISCONNECTED) {
            runCatching {
                val field = ZoyaSessionManager::class.java.getDeclaredField("client")
                field.isAccessible = true
                val client = field.get(ZoyaSessionManager) as? GeminiLiveClient
                if (client != null) {
                    client.sendText(prompt)
                    return
                }
            }
        }

        val announcement = prompt
            .substringAfter("[PROACTIVE SYSTEM EVENT]", prompt)
            .substringBefore("Speak to the user proactively")
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { prompt.replace(Regex("\\s+"), " ").trim() }
        speakWithoutMic(app, announcement)
    }

    private fun speakWithoutMic(context: Context, text: String) {
        if (text.isBlank()) return
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = engine ?: return@TextToSpeech
                val locale = when (ZoyaSessionManager.state.value.language) {
                    ZoyaLanguage.HINDI -> Locale("hi", "IN")
                    ZoyaLanguage.SANTALI -> Locale("en", "IN")
                    ZoyaLanguage.ODIA -> Locale("en", "IN")
                    ZoyaLanguage.ENGLISH -> Locale.US
                }
                runCatching { tts.language = locale }
                val params = Bundle()
                val utteranceId = "anu_proactive_${System.currentTimeMillis()}"
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
        }
    }
}
