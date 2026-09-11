package com.ghaniram.zoya

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Direct Gemini Live API WebSocket client with long-session resumption and robust turn completions. */
class GeminiLiveClient(
    private val apiKey: String,
    private val model: String = "models/gemini-3.1-flash-live-preview",
    private val voiceName: String = "Kore",
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
        fun onAudioChunk(base64Pcm: String)
        fun onModelText(text: String)
        fun onUserText(text: String)
        fun onInterrupted()
        fun onTurnComplete()
        fun onToolCall(name: String, args: JSONObject, id: String)
    }

    private var webSocket: WebSocket? = null
    private var setupComplete = false
    private var terminalErrorSent = false
    private val pendingMessages = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenVisionExecutor: ScheduledExecutorService? = null
    @Volatile private var lastScreenFrameHash: String? = null
    private val setupTimeout = Runnable {
        if (!setupComplete && webSocket != null) fail("Gemini Live setup timed out. Check API key, Live API access, model availability, and internet connection.")
    }
    private var latestResumptionHandle: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect(systemInstruction: String, tools: JSONArray) {
        disconnectSilently()
        if (apiKey.isBlank()) {
            callbacks.onError("Gemini Live API key is missing. Please add your key in Anu Settings -> Personal.")
            callbacks.onDisconnected()
            return
        }
        setupComplete = false
        terminalErrorSent = false
        pendingMessages.clear()
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val setupConfig = JSONObject().apply {
                    put("model", model)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().put("AUDIO"))
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
                            })
                        })
                    })
                    put("outputAudioTranscription", JSONObject())
                    put("inputAudioTranscription", JSONObject())
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction + " You are Anu, a proactive personal assistant. You may speak freely and assist the user with any task."))))
                    if (tools.length() > 0) put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
                }
                if (!ws.send(JSONObject().put("setup", setupConfig).toString())) {
                    fail("Could not send Gemini Live setup message.")
                    return
                }
                mainHandler.removeCallbacks(setupTimeout)
                mainHandler.postDelayed(setupTimeout, 15_000L)
            }
            override fun onMessage(ws: WebSocket, text: String) { handleServerMessage(text) }
            override fun onMessage(ws: WebSocket, bytes: ByteString) { val text = bytes.utf8(); if (text.trimStart().startsWith("{")) handleServerMessage(text) }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                mainHandler.removeCallbacks(setupTimeout)
                val http = response?.let { " HTTP ${it.code}" } ?: ""
                fail("Gemini Live WebSocket failed$http: ${t.message ?: "unknown network error"}")
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                mainHandler.removeCallbacks(setupTimeout)
                setupComplete = false
                stopScreenVision()
                if (webSocket === ws) webSocket = null
                callbacks.onDisconnected()
            }
        })
    }

    private fun handleServerMessage(text: String) {
        val json = try { JSONObject(text) } catch (_: Exception) { return }
        json.optJSONObject("sessionResumptionUpdate")?.let { update ->
            if (update.optBoolean("resumable", false)) update.optString("newHandle").takeIf { it.isNotBlank() }?.let { latestResumptionHandle = it }
        }
        json.optJSONObject("goAway")?.let {
            callbacks.onError("Gemini Live session is renewing; reconnecting Anu…")
            webSocket?.close(1000, "Live API GoAway")
            return
        }
        if (json.has("setupComplete")) {
            mainHandler.removeCallbacks(setupTimeout)
            setupComplete = true
            terminalErrorSent = false
            callbacks.onConnected()
            flushPendingMessages()
            startScreenVision()
            return
        }
        json.optJSONObject("error")?.let { error ->
            val code = if (error.has("code")) " (${error.optInt("code")})" else ""
            val status = error.optString("status")
            val message = error.optString("message").ifBlank { error.toString() }
            fail("Gemini Live API error$code${if (status.isNotBlank()) " $status" else ""}: $message")
            return
        }
        json.optJSONObject("serverContent")?.let { content ->
            if (content.optBoolean("interrupted", false)) callbacks.onInterrupted()
            content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    part.optString("text").takeIf { it.isNotBlank() }?.let { callbacks.onModelText(it) }
                    part.optJSONObject("inlineData")?.optString("data")?.takeIf { it.isNotBlank() }?.let { callbacks.onAudioChunk(it) }
                }
            }
            content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let { callbacks.onModelText(it) }
            content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let { callbacks.onUserText(it) }
            if (content.optBoolean("turnComplete", false)) callbacks.onTurnComplete()
        }
        json.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                callbacks.onToolCall(call.optString("name"), call.optJSONObject("args") ?: JSONObject(), call.optString("id"))
            }
        }
    }

    private fun fail(message: String) {
        if (terminalErrorSent) return
        terminalErrorSent = true
        setupComplete = false
        stopScreenVision()
        pendingMessages.clear()
        mainHandler.removeCallbacks(setupTimeout)
        callbacks.onError(message)
        webSocket?.cancel()
        webSocket = null
        callbacks.onDisconnected()
    }

    private fun enqueueOrSend(message: String) {
        if (message.isBlank()) return
        if (!setupComplete) { if (pendingMessages.size < 64) pendingMessages.addLast(message); return }
        webSocket?.send(message)
    }

    private fun flushPendingMessages() { while (setupComplete && pendingMessages.isNotEmpty()) webSocket?.send(pendingMessages.removeFirst()) }

    fun sendAudioChunk(base64Pcm: String) {
        if (!setupComplete || base64Pcm.isBlank()) return
        webSocket?.send(JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject().apply { put("data", base64Pcm); put("mimeType", "audio/pcm;rate=16000") })).toString())
    }

    fun sendVideoFrame(base64Jpeg: String) {
        if (base64Jpeg.isBlank() || !setupComplete) return
        webSocket?.send(JSONObject().put("realtimeInput", JSONObject().put("video", JSONObject().apply { put("data", base64Jpeg); put("mimeType", "image/jpeg") })).toString())
    }

    /**
     * Sends prompt/text to Gemini Live as a completed client turn.
     * Setting turnComplete = true instructs Gemini Live to immediately generate audio/text response.
     */
    fun sendText(text: String) {
        if (text.isBlank()) return
        val payload = JSONObject().put("clientContent", JSONObject().apply {
            put("turns", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            }))
            put("turnComplete", true)
        }).toString()
        enqueueOrSend(payload)
    }

    fun sendVisionImage(base64Jpeg: String, prompt: String) { if (base64Jpeg.isBlank()) return; sendVideoFrame(base64Jpeg); sendText(prompt) }

    fun sendToolResponse(name: String, id: String, output: String) {
        if (name.isBlank() || id.isBlank()) return
        val response = JSONObject().put("result", output)
        val functionResponse = JSONObject().apply {
            put("name", name)
            put("id", id)
            put("response", response)
        }
        val functionResponses = JSONArray().put(functionResponse)
        val toolResponse = JSONObject().put("functionResponses", functionResponses)
        enqueueOrSend(JSONObject().put("toolResponse", toolResponse).toString())
    }

    /** Start low-rate pixel sampling from the user's enabled AccessibilityService. */
    private fun startScreenVision() {
        stopScreenVision()
        screenVisionExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Anu-ScreenVision").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay({
                if (!setupComplete) return@scheduleWithFixedDelay
                AccessibilityControlService.instance?.captureScreenJpeg { bytes ->
                    if (bytes.isNotEmpty() && setupComplete) {
                        val hash = sha256(bytes)
                        if (hash == lastScreenFrameHash) return@captureScreenJpeg
                        lastScreenFrameHash = hash
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        sendVideoFrame(base64)
                    }
                }
            }, 500L, 2200L, TimeUnit.MILLISECONDS)
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun stopScreenVision() {
        screenVisionExecutor?.shutdownNow()
        screenVisionExecutor = null
        lastScreenFrameHash = null
    }

    fun disconnect() {
        mainHandler.removeCallbacks(setupTimeout)
        setupComplete = false
        terminalErrorSent = false
        stopScreenVision()
        pendingMessages.clear()
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

    private fun disconnectSilently() {
        mainHandler.removeCallbacks(setupTimeout)
        setupComplete = false
        stopScreenVision()
        pendingMessages.clear()
        webSocket?.cancel()
        webSocket = null
    }
}
