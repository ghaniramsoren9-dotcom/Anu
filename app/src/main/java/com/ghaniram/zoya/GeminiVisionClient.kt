package com.ghaniram.zoya

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One-shot multimodal Vision client. Keeps image + instruction in the same request so they cannot race. */
class GeminiVisionClient(private val apiKey: String) {
    interface Callback {
        fun onSuccess(text: String)
        fun onError(message: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val visionModels = listOf(
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-2.5-pro",
        "gemini-2.5-flash-image"
    )

    fun analyze(jpegBytes: ByteArray, instruction: String, callback: Callback) {
        if (apiKey.isBlank()) {
            callback.onError("Gemini API key is missing. Please add your key in Anu Settings -> Personal.")
            return
        }
        if (jpegBytes.isEmpty()) {
            callback.onError("Camera image is empty.")
            return
        }

        executeWithModel(jpegBytes, instruction, 0, callback)
    }

    private fun executeWithModel(
        jpegBytes: ByteArray,
        instruction: String,
        modelIndex: Int,
        callback: Callback
    ) {
        if (modelIndex >= visionModels.size) {
            callback.onError("All Gemini vision models failed to respond.")
            return
        }

        val modelName = visionModels[modelIndex]
        val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray()
                    .put(JSONObject().put("text", instruction))
                    .put(JSONObject().put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", encoded)
                    }))
                )
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (modelIndex + 1 < visionModels.size) {
                    executeWithModel(jpegBytes, instruction, modelIndex + 1, callback)
                } else {
                    callback.onError("Vision network error: ${e.message ?: "unknown error"}")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        // If 404 model not found or unavailable, fallback to next model
                        if ((it.code == 404 || it.code == 400) && modelIndex + 1 < visionModels.size) {
                            executeWithModel(jpegBytes, instruction, modelIndex + 1, callback)
                            return
                        }
                        callback.onError("Vision API error HTTP ${it.code}: ${extractError(raw)}")
                        return
                    }
                    try {
                        val json = JSONObject(raw)
                        val candidates = json.optJSONArray("candidates") ?: JSONArray()
                        val parts = candidates.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                        val text = buildString {
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    parts.optJSONObject(i)?.optString("text")
                                        ?.takeIf { value -> value.isNotBlank() }
                                        ?.let(::append)
                                }
                            }
                        }.trim()
                        if (text.isBlank()) {
                            callback.onError("Vision returned no text result.")
                        } else {
                            callback.onSuccess(text)
                        }
                    } catch (e: Exception) {
                        callback.onError("Invalid Vision response: ${e.message ?: "parse error"}")
                    }
                }
            }
        })
    }

    private fun extractError(raw: String): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown error"
}
