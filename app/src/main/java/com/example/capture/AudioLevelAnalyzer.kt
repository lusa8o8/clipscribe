package com.example.capture

import kotlin.math.sqrt

object AudioLevelAnalyzer {
    const val AUDIO_DETECTION_RMS_THRESHOLD = 0.01

    fun calculateRms(buffer: ShortArray, readCount: Int): Double {
        if (readCount <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until readCount) {
            val sample = buffer[i] / 32768.0 // normalize 16-bit short to [-1.0, 1.0]
            sum += sample * sample
        }
        return sqrt(sum / readCount)
    }

    fun hasMeaningfulAudio(rms: Double): Boolean {
        return rms >= AUDIO_DETECTION_RMS_THRESHOLD
    }
}
