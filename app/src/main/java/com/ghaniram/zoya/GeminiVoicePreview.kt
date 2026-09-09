package com.ghaniram.zoya

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Low-latency Gemini TTS voice preview.
 * - Short phrase (faster synthesis)
 * - In-memory cache per voice (2nd play is instant)
 * - Model fallback if primary TTS model unavailable
 */
object GeminiVoicePreview {
    private const val TAG = "GeminiVoicePreview"
    private val MODELS = listOf(
        "gemini-2.5-flash-preview-tts",   // fastest / cheapest
        "gemini-3.1-flash-tts-preview"    // fallback
    )
    // Keep phrase short — generation time scales with length
    private const val DEFAULT_PHRASE = "Hi, I'm Anu."

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val cache = ConcurrentHashMap<String, ByteArray>()

    @Volatile private var currentTrack: AudioTrack? = null

    fun stop() {
        runCatching {
            currentTrack?.pause()
            currentTrack?.stop()
            currentTrack?.release()
        }
        currentTrack = null
    }

    fun clearCache() {
        cache.clear()
    }

    /**
     * @return null on success, or an error message string.
     */
    suspend fun playPreview(
        apiKey: String,
        voiceName: String,
        phrase: String = DEFAULT_PHRASE
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Add your Gemini API key in Settings → Personal first."
        }
        stop()

        val cacheKey = "$voiceName|$phrase"
        val cached = cache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            playPcm(cached)
            return@withContext null
        }

        var lastError: String? = null
        for (model in MODELS) {
            val result = fetchPcm(apiKey, model, voiceName, phrase)
            if (result.isSuccess) {
                val pcm = result.getOrNull()!!
                cache[cacheKey] = pcm
                // Bound cache size
                if (cache.size > 40) {
                    val first = cache.keys.firstOrNull()
                    if (first != null) cache.remove(first)
                }
                playPcm(pcm)
                return@withContext null
            }
            lastError = result.exceptionOrNull()?.message ?: "Unknown error"
            Log.w(TAG, "Model $model failed: $lastError")
        }
        lastError ?: "Voice preview failed. Check API key and internet."
    }

    private fun fetchPcm(
        apiKey: String,
        model: String,
        voiceName: String,
        phrase: String
    ): Result<ByteArray> {
        return runCatching {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", phrase)
                ))))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put(
                        "prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)
                    )))
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(raw).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    error("${resp.code}${if (!detail.isNullOrBlank()) ": $detail" else ""}")
                }
                extractPcm(raw) ?: error("No audio data in response")
            }
        }
    }

    private fun extractPcm(json: String): ByteArray? {
        return runCatching {
            val root = JSONObject(json)
            val parts = root
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val inline = when {
                    part.has("inlineData") -> part.getJSONObject("inlineData")
                    part.has("inline_data") -> part.getJSONObject("inline_data")
                    else -> null
                } ?: continue
                val data = inline.optString("data")
                if (data.isBlank()) continue
                var bytes = Base64.decode(data, Base64.DEFAULT)
                // Strip WAV header if present (RIFF....WAVE)
                if (bytes.size > 44 &&
                    bytes[0] == 'R'.code.toByte() &&
                    bytes[1] == 'I'.code.toByte() &&
                    bytes[2] == 'F'.code.toByte() &&
                    bytes[3] == 'F'.code.toByte()
                ) {
                    bytes = bytes.copyOfRange(44, bytes.size)
                }
                if (bytes.isNotEmpty()) return@runCatching bytes
            }
            null
        }.getOrNull()
    }

    private fun playPcm(pcm: ByteArray) {
        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(minBuf))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        currentTrack = track
        track.write(pcm, 0, pcm.size)
        track.play()
    }
}
