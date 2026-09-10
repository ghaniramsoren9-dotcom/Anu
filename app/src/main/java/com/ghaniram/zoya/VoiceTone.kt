package com.ghaniram.zoya

import kotlin.math.sqrt

/** Lightweight, privacy-conscious voice cues used only to adapt response style. */
data class VoiceTone(val label: Label, val confidence: Float) {
    enum class Label { CALM, NEUTRAL, URGENT, FRUSTRATED }
}

object VoiceToneEstimator {
    fun estimate(samples: ShortArray, sampleRate: Int): VoiceTone {
        if (samples.isEmpty() || sampleRate <= 0) return VoiceTone(VoiceTone.Label.NEUTRAL, 0f)
        var energy = 0.0
        var crossings = 0
        var previous = samples[0].toInt()
        for (i in samples.indices) {
            val x = samples[i].toInt()
            energy += x.toDouble() * x.toDouble()
            if (i > 0 && ((x >= 0) != (previous >= 0))) crossings++
            previous = x
        }
        val rms = sqrt(energy / samples.size) / Short.MAX_VALUE
        val zcr = crossings.toFloat() / samples.size
        return when {
            rms > 0.30 && zcr > 0.08 -> VoiceTone(VoiceTone.Label.URGENT, 0.72f)
            rms > 0.18 && zcr > 0.05 -> VoiceTone(VoiceTone.Label.FRUSTRATED, 0.62f)
            rms < 0.035 -> VoiceTone(VoiceTone.Label.CALM, 0.55f)
            else -> VoiceTone(VoiceTone.Label.NEUTRAL, 0.50f)
        }
    }
}

object VoiceResponsePolicy {
    fun instruction(tone: VoiceTone): String = when (tone.label) {
        VoiceTone.Label.URGENT -> "Respond immediately and concisely; prioritize the requested action."
        VoiceTone.Label.FRUSTRATED -> "Respond calmly, acknowledge the difficulty briefly, and give the clearest next step."
        VoiceTone.Label.CALM -> "Use a calm, natural conversational response."
        VoiceTone.Label.NEUTRAL -> "Use a natural, concise conversational response."
    }
}
