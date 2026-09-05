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
import java.util.concurrent.TimeUnit

/** Direct Gemini Live API WebSocket client. */
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val setupTimeout = Runnable {
        if (!setupComplete && webSocket != null) {
            fail("Gemini Live setup timed out. Check API key, Live API access, model availability, and internet connection.")
        }
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect(systemInstruction: String, tools: JSONArray) {
        disconnectSilently()
        if (apiKey.isBlank()) {
            callbacks.onError("Gemini API key is missing. Please add your key in Anu Settings -> Personal.")
            callbacks.onDisconnected()
            return
        }
        setupComplete = false
        terminalErrorSent = false
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
                    put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction + " For current India time or the user's current device location, call getDeviceInfo and use its India time/location fields as ground truth. For a request to call a named contact, use accessibilityAction with action=call_contact and text equal to the contact name; do not open the Dialer or merely tell the user to call manually. Execute the tool before claiming the call was placed."))))
                    if (tools.length() > 0) {
                        put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))
                    }
                }
                if (!ws.send(JSONObject().put("setup", setupConfig).toString())) {
                    fail("Could not send Gemini Live setup message.")
                    return
                }
                mainHandler.removeCallbacks(setupTimeout)
                mainHandler.postDelayed(setupTimeout, 15_000L)
            }

            override fun onMessage(ws: WebSocket, text: String) { handleServerMessage(text) }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                if (text.trimStart().startsWith("{")) handleServerMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                mainHandler.removeCallbacks(setupTimeout)
                val http = response?.let { " HTTP ${it.code}" } ?: ""
                fail("Gemini Live WebSocket failed$http: ${t.message ?: "unknown network error"}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                mainHandler.removeCallbacks(setupTimeout)
                setupComplete = false
                if (webSocket === ws) webSocket = null
                callbacks.onDisconnected()
            }
        })
    }

    private fun handleServerMessage(text: String) {
        val json = try { JSONObject(text) } catch (_: Exception) { return }
        if (json.has("setupComplete")) {
            mainHandler.removeCallbacks(setupTimeout)
            setupComplete = true
            terminalErrorSent = false
            callbacks.onConnected()
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
                    part.optString("text").takeIf { it.isNotBlank() }?.let(callbacks::onModelText)
                    part.optJSONObject("inlineData")?.optString("data")?.takeIf { it.isNotBlank() }?.let(callbacks::onAudioChunk)
                }
            }
            content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let(callbacks::onModelText)
            content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() }?.let(callbacks::onUserText)
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
        mainHandler.removeCallbacks(setupTimeout)
        callbacks.onError(message)
        webSocket?.cancel()
        webSocket = null
        callbacks.onDisconnected()
    }

    fun sendAudioChunk(base64Pcm: String) {
        if (!setupComplete) return
        webSocket?.send(JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject().apply {
            put("data", base64Pcm)
            put("mimeType", "audio/pcm;rate=16000")
        })).toString())
    }

    /** Sends a live camera frame. Gemini Live accepts JPEG/PNG frames at up to 1 FPS. */
    fun sendVideoFrame(base64Jpeg: String) {
        if (!setupComplete || base64Jpeg.isBlank()) return
        webSocket?.send(JSONObject().put("realtimeInput", JSONObject().put("video", JSONObject().apply {
            put("data", base64Jpeg)
            put("mimeType", "image/jpeg")
        })).toString())
    }

    fun sendText(text: String) {
        if (!setupComplete || text.isBlank()) return
        webSocket?.send(JSONObject().put("realtimeInput", JSONObject().put("text", text)).toString())
    }

    /** Runs a deterministic one-shot Vision request with the captured camera image and prompt in one atomic payload. */
    fun sendVisionImage(base64Jpeg: String, prompt: String) {
        if (!setupComplete || base64Jpeg.isBlank()) return
        val imageBytes = try {
            android.util.Base64.decode(base64Jpeg, android.util.Base64.DEFAULT)
        } catch (_: Exception) {
            callbacks.onError("Invalid camera image data.")
            callbacks.onTurnComplete()
            return
        }
        GeminiVisionClient(apiKey).analyze(imageBytes, prompt, object : GeminiVisionClient.Callback {
            override fun onSuccess(text: String) {
                callbacks.onModelText(text)
                callbacks.onTurnComplete()
            }

            override fun onError(message: String) {
                callbacks.onError(message)
                callbacks.onTurnComplete()
            }
        })
    }

    fun sendToolResponse(name: String, id: String, output: String) {
        if (!setupComplete) return
        webSocket?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(JSONObject().apply {
            put("name", name)
            put("id", id)
            put("response", JSONObject().put("result", output))
        }))).toString())
    }

    fun disconnect() {
        mainHandler.removeCallbacks(setupTimeout)
        setupComplete = false
        terminalErrorSent = false
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

    private fun disconnectSilently() {
        mainHandler.removeCallbacks(setupTimeout)
        setupComplete = false
        webSocket?.cancel()
        webSocket = null
    }
}
