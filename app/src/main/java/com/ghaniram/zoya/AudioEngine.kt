package com.ghaniram.zoya

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min

/** Dedicated PCM capture/playback engine. Assistant speech uses media routing (loudspeaker). */
class AudioEngine(
    private val onMicChunkBase64: (String) -> Unit,
    private val onInputLevel: (Float) -> Unit,
    private val onOutputLevel: (Float) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordJob: Job? = null
    private var playbackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private val pendingPlaybackBytes = AtomicLong(0L)
    private val totalFramesWritten = AtomicLong(0L)

    companion object { const val INPUT_SAMPLE_RATE = 16000; const val OUTPUT_SAMPLE_RATE = 24000 }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (recordJob != null) return
        val minBuf = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { onInputLevel(0f); return }
        val bufferSize = maxOf(minBuf * 2, 4096)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, INPUT_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        audioRecord = record
        try { record.startRecording() } catch (_: Exception) { record.release(); audioRecord = null; onInputLevel(0f); return }
        recordJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize / 2)
            while (isActive && audioRecord === record) {
                val read = try { record.read(buffer, 0, buffer.size) } catch (_: Exception) { -1 }
                if (read > 0) {
                    var sum = 0.0; for (i in 0 until read) sum += abs(buffer[i].toInt())
                    onInputLevel(min(1f, ((sum / read).toFloat() / Short.MAX_VALUE) * 6f))
                    onMicChunkBase64(Base64.encodeToString(shortsToBytes(buffer, read), Base64.NO_WRAP))
                }
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel(); recordJob = null
        audioRecord?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        audioRecord = null; onInputLevel(0f)
    }

    fun startPlayback() {
        if (audioTrack != null) return
        val minBuf = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return
        val bufferSize = maxOf(minBuf * 2, 16384)
        val track = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            AudioFormat.Builder().setSampleRate(OUTPUT_SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack = track; playbackQueue.clear(); pendingPlaybackBytes.set(0L); totalFramesWritten.set(0L)
        try { track.setVolume(1f) } catch (_: Exception) {}
        try { track.play() } catch (_: Exception) { track.release(); audioTrack = null; return }
        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val bytes = try { playbackQueue.poll(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { null } ?: continue
                val current = audioTrack ?: break; var offset = 0; var writtenTotal = 0
                while (offset < bytes.size && isActive && audioTrack === current) {
                    val written = try { current.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_BLOCKING) } catch (_: Exception) { -1 }
                    if (written <= 0) break
                    offset += written; writtenTotal += written
                }
                totalFramesWritten.addAndGet((writtenTotal / 2).toLong())
                pendingPlaybackBytes.addAndGet(-bytes.size.toLong()); updateOutputLevel(bytes)
            }
        }
    }

    fun playChunkBase64(b64: String) { try { val bytes = Base64.decode(b64, Base64.NO_WRAP); if (bytes.isNotEmpty() && audioTrack != null) { pendingPlaybackBytes.addAndGet(bytes.size.toLong()); playbackQueue.put(bytes) } } catch (_: Exception) {} }

    fun whenPlaybackDrained(callback: () -> Unit) {
        scope.launch(Dispatchers.Default) {
            while (isActive && pendingPlaybackBytes.get() > 0L) delay(10)
            val target = totalFramesWritten.get() and 0xFFFFFFFFL
            while (isActive) { val track = audioTrack ?: break; val played = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL; if (((target - played) and 0xFFFFFFFFL) == 0L) break; delay(10) }
            if (isActive) mainHandler.post(callback)
        }
    }

    fun flushPlayback() { playbackQueue.clear(); pendingPlaybackBytes.set(0L); totalFramesWritten.set(0L); audioTrack?.let { try { it.pause(); it.flush(); it.play() } catch (_: Exception) {} }; onOutputLevel(0f) }

    private fun updateOutputLevel(bytes: ByteArray) { var sum = 0.0; var i = 0; var count = 0; while (i + 1 < bytes.size) { val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort(); sum += abs(sample.toInt()); i += 2; count++ }; if (count > 0) onOutputLevel(min(1f, ((sum / count).toFloat() / Short.MAX_VALUE) * 6f)) }

    fun stopPlayback() { playbackJob?.cancel(); playbackJob = null; playbackQueue.clear(); pendingPlaybackBytes.set(0L); totalFramesWritten.set(0L); audioTrack?.let { try { it.stop() } catch (_: Exception) {}; it.release() }; audioTrack = null; onOutputLevel(0f) }
    fun release() { stopRecording(); stopPlayback() }
    private fun shortsToBytes(shorts: ShortArray, length: Int): ByteArray { val bytes = ByteArray(length * 2); for (i in 0 until length) { val v = shorts[i].toInt(); bytes[i * 2] = (v and 0xFF).toByte(); bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte() }; return bytes }
}
