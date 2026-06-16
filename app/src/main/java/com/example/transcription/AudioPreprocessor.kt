package com.example.transcription

object AudioPreprocessor {
    fun shortPcmToFloat(samples: ShortArray): FloatArray {
        val floatSamples = FloatArray(samples.size)
        for (i in samples.indices) {
            floatSamples[i] = samples[i] / 32768.0f
        }
        return floatSamples
    }

    fun calculateDurationSeconds(sampleCount: Int, sampleRate: Int): Double {
        if (sampleRate <= 0) return 0.0
        return sampleCount.toDouble() / sampleRate
    }

    fun prepare(samples: ShortArray, sampleRate: Int, includeWavBytes: Boolean): PreparedAudio {
        val floatSamples = shortPcmToFloat(samples)
        val duration = calculateDurationSeconds(samples.size, sampleRate)
        val wavBytes = if (includeWavBytes) {
            WavEncoder.encodePcmToWav(samples, sampleRate)
        } else {
            null
        }
        return PreparedAudio(
            floatSamples = floatSamples,
            sampleRate = sampleRate,
            durationSeconds = duration,
            sampleCount = samples.size,
            wavBytes = wavBytes
        )
    }
}
