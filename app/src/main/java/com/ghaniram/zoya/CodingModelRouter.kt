package com.ghaniram.zoya

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runtime router for coding sub-agents.
 *
 * The persisted codingModelsOrder is treated as an ordered fallback chain.
 * Gemini uses the native generateContent API. Custom providers use an
 * OpenAI-compatible chat-completions endpoint configured in customProvidersJson.
 * A provider failure automatically advances to the next configured model.
 */
class CodingModelRouter(
    private val settings: AnuSettingsStore
) {
    interface Callback {
        fun onSuccess(text: String, model: String)
        fun onError(message: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Provider(
        val name: String,
        val baseUrl: String,
        val apiKey: String,
        val models: Set<String>
    )

    fun generate(prompt: String, callback: Callback) {
        if (prompt.isBlank()) {
            callback.onError("Coding prompt is empty.")
            return
        }

        val chain = settings.codingModelsOrder
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (chain.isEmpty()) {
            callback.onError("No coding models are configured.")
            return
        }

        execute(chain, prompt, 0, null, callback)
    }

    private fun execute(
        chain: List<String>,
        prompt: String,
        index: Int,
        lastError: String?,
        callback: Callback
    ) {
        if (index >= chain.size) {
            callback.onError(
                "All configured coding models failed${lastError?.let { ": $it" } ?: "."}"
            )
            return
        }

        val model = chain[index]
        val providers = parseProviders(settings.customProvidersJson)
        val provider = providers.firstOrNull { model in it.models }

        if (model.startsWith("gemini-")) {
            val key = settings.customGeminiKey.trim()
            if (key.isBlank()) {
                execute(chain, prompt, index + 1, "Gemini API key is missing", callback)
                return
            }
            callGemini(model, key, prompt) { result ->
                result.fold(
                    onSuccess = { callback.onSuccess(it, model) },
                    onFailure = {
                        execute(chain, prompt, index + 1, it.message ?: "Gemini request failed", callback)
                    }
                )
            }
            return
        }

        if (provider == null || provider.baseUrl.isBlank() || provider.apiKey.isBlank()) {
            execute(chain, prompt, index + 1, "Provider configuration missing for $model", callback)
            return
        }

        callOpenAiCompatible(provider, model, prompt) { result ->
            result.fold(
                onSuccess = { callback.onSuccess(it, model) },
                onFailure = {
                    execute(chain, prompt, index + 1, it.message ?: "Provider request failed", callback)
                }
            )
        }
    }

    private fun callGemini(
        model: String,
        apiKey: String,
        prompt: String,
        done: (Result<String>) -> Unit
    ) {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.15)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                done(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        done(Result.failure(IOException("Gemini HTTP ${it.code}: ${extractError(raw)}")))
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
                            done(Result.failure(IOException("Gemini returned no text.")))
                        } else {
                            done(Result.success(text))
                        }
                    } catch (e: Exception) {
                        done(Result.failure(IOException("Invalid Gemini response: ${e.message}")))
                    }
                }
            }
        })
    }

    private fun callOpenAiCompatible(
        provider: Provider,
        model: String,
        prompt: String,
        done: (Result<String>) -> Unit
    ) {
        val url = provider.baseUrl.trimEnd('/') + "/chat/completions"
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
            put("temperature", 0.15)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${provider.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                done(Result.failure(e))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        done(Result.failure(IOException("${provider.name} HTTP ${it.code}: ${extractError(raw)}")))
                        return
                    }
                    try {
                        val json = JSONObject(raw)
                        val choices = json.optJSONArray("choices") ?: JSONArray()
                        val text = choices.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content")
                            .orEmpty()
                            .trim()
                        if (text.isBlank()) {
                            done(Result.failure(IOException("${provider.name} returned no text.")))
                        } else {
                            done(Result.success(text))
                        }
                    } catch (e: Exception) {
                        done(Result.failure(IOException("Invalid ${provider.name} response: ${e.message}")))
                    }
                }
            }
        })
    }

    private fun parseProviders(raw: String): List<Provider> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val models = buildSet {
                    val modelArray = obj.optJSONArray("models")
                    if (modelArray != null) {
                        for (j in 0 until modelArray.length()) {
                            modelArray.optString(j).trim().takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    obj.optString("model").trim().takeIf { it.isNotBlank() }?.let(::add)
                }
                add(
                    Provider(
                        name = obj.optString("name", "Custom provider"),
                        baseUrl = obj.optString("baseUrl", obj.optString("base_url")),
                        apiKey = obj.optString("apiKey", obj.optString("api_key")),
                        models = models
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun extractError(raw: String): String = runCatching {
        JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown error"
}
