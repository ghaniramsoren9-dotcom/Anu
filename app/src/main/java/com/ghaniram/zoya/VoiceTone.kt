package com.ghaniram.zoya

import kotlin.math.sqrt

/**
 * Lightweight conversational delivery cue. This is not emotion or mental-health diagnosis.
 * It only summarizes current PCM energy/zero-crossing characteristics for response style.
 */
enum class VoiceTone(val label: String) {
    CALM("calm"),
    NEUTRAL("neutral"),
    ENERGETIC("energetic"),
    URGENT("urgent"),
    STRAINED("strained")
}

data class VoiceToneSnapshot(
    val tone: VoiceTone = VoiceTone.NEUTRAL,
    val confidence: Float = 0f
)

class VoiceToneEstimator {
    private var stableTone = VoiceTone.NEUTRAL
    private var stableConfidence = 0f
    private var candidateTone = VoiceTone.NEUTRAL
    private var candidateFrames = 0

    fun update(samples: ShortArray, length: Int): VoiceToneSnapshot {
        if (length < 160) return VoiceToneSnapshot(stableTone, stableConfidence)
        var sumSquares = 0.0
        var zeroCrossings = 0
        var previous = samples[0].toInt()
        for (i in 0 until length) {
            val current = samples[i].toInt()
            sumSquares += current.toDouble() * current.toDouble()
            if ((previous < 0 && current >= 0) || (previous >= 0 && current < 0)) zeroCrossings++
            previous = current
        }
        val rms = sqrt(sumSquares / length) / Short.MAX_VALUE
        if (rms < 0.015) return VoiceToneSnapshot(stableTone, stableConfidence * 0.92f)

        val zcr = zeroCrossings.toFloat() / length
        val energy = rms.toFloat().coerceIn(0f, 1f)
        val (tone, confidence) = when {
            energy > 0.28f && zcr > 0.10f -> VoiceTone.URGENT to 0.72f
            energy > 0.18f && zcr > 0.07f -> VoiceTone.ENERGETIC to 0.66f
            energy < 0.045f && zcr < 0.075f -> VoiceTone.CALM to 0.60f
            energy > 0.16f && zcr < 0.045f -> VoiceTone.STRAINED to 0.55f
            else -> VoiceTone.NEUTRAL to 0.55f
        }
        if (tone == candidateTone) candidateFrames++ else {
            candidateTone = tone
            candidateFrames = 1
        }
        if (candidateFrames >= 3 || tone == stableTone) {
            stableTone = candidateTone
            stableConfidence = confidence
        }
        return VoiceToneSnapshot(stableTone, stableConfidence)
    }
}
