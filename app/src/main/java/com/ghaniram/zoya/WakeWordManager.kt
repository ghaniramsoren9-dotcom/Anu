package com.ghaniram.zoya

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Lightweight fallback hotword listener. It uses Android SpeechRecognizer to listen
 * for "Hey Anu" while the full Gemini microphone session is stopped.
 *
 * This is not a hardware/DSP hotword detector: Android still considers the microphone
 * in use while this listener is active. A true Google-Assistant-style always-on hotword
 * requires device/system hotword support or a bundled on-device keyword model.
 */
class WakeWordManager(
    private val context: Context,
    private val onWake: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var restarting = false

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        running = true
        main.post { createAndListen() }
    }

    fun stop() {
        running = false
        main.post {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun createAndListen() {
        if (!running) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { scheduleRestart() }
                override fun onError(error: Int) { scheduleRestart() }
                override fun onResults(results: Bundle?) {
                    val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (phrases.any { isWakePhrase(it) }) {
                        running = false
                        main.post {
                            recognizer?.cancel()
                            recognizer?.destroy()
                            recognizer = null
                            onWake()
                        }
                    } else scheduleRestart()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val phrases = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (phrases.any { isWakePhrase(it) }) {
                        running = false
                        recognizer?.cancel()
                        recognizer?.destroy()
                        recognizer = null
                        onWake()
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { scheduleRestart() }
    }

    private fun scheduleRestart() {
        if (!running || restarting) return
        restarting = true
        main.postDelayed({
            restarting = false
            if (running) createAndListen()
        }, 250L)
    }

    private fun isWakePhrase(value: String): Boolean {
        val normalized = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        return normalized == "hey anu" || normalized.contains("hey anu")
    }
}
