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
import java.util.concurrent.TimeUnit

/**
 * Fetches a short natural-voice sample from Gemini TTS and plays it.
 * Uses the same prebuilt voice names as Gemini Live (Aoede, Kore, …).
 */
object GeminiVoicePreview {
    private const val TAG = "GeminiVoicePreview"
    // Prefer the widely available flash TTS model; fall back handled by caller if needed
    private const val MODEL = "gemini-2.5-flash-preview-tts"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    @Volatile private var currentTrack: AudioTrack? = null

    fun stop() {
        runCatching {
            currentTrack?.stop()
            currentTrack?.release()
        }
        currentTrack = null
    }

    /**
     * @return null on success, or an error message string.
     */
    suspend fun playPreview(
        apiKey: String,
        voiceName: String,
        phrase: String = "Hello! I am Anu. How can I help you today?"
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Add your Gemini API key in Settings → Personal first."
        stop()

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
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrElse {
            return@withContext "Network error: ${it.message}"
        }

        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "TTS failed ${resp.code}: $raw")
                return@withContext "Voice preview failed (${resp.code}). Check API key / model access."
            }
            val pcm = extractPcm(raw)
                ?: return@withContext "No audio in response. Try again."
            playPcm24k(pcm)
            null
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
                if (part.has("inlineData")) {
                    val data = part.getJSONObject("inlineData").optString("data")
                    if (data.isNotBlank()) return@runCatching Base64.decode(data, Base64.DEFAULT)
                }
                if (part.has("inline_data")) {
                    val data = part.getJSONObject("inline_data").optString("data")
                    if (data.isNotBlank()) return@runCatching Base64.decode(data, Base64.DEFAULT)
                }
            }
            null
        }.getOrNull()
    }

    private fun playPcm24k(pcm: ByteArray) {
        val sampleRate = 24000
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
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(sampleRate))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        currentTrack = track
        track.write(pcm, 0, pcm.size)
        track.play()
    }
}
