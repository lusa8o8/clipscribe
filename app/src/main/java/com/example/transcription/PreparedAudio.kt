package com.example.transcription

data class PreparedAudio(
    val floatSamples: FloatArray,
    val sampleRate: Int,
    val durationSeconds: Double,
    val sampleCount: Int,
    val wavBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PreparedAudio

        if (!floatSamples.contentEquals(other.floatSamples)) return false
        if (sampleRate != other.sampleRate) return false
        if (durationSeconds != other.durationSeconds) return false
        if (sampleCount != other.sampleCount) return false
        if (wavBytes != null) {
            if (other.wavBytes == null) return false
            if (!wavBytes.contentEquals(other.wavBytes)) return false
        } else if (other.wavBytes != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = floatSamples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + durationSeconds.hashCode()
        result = 31 * result + sampleCount
        result = 31 * result + (wavBytes?.contentHashCode() ?: 0)
        return result
    }
}
