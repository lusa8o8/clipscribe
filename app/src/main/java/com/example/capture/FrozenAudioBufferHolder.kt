package com.example.capture

object FrozenAudioBufferHolder {
    @Volatile
    private var frozenBuffer: ShortArray? = null

    @Volatile
    private var sampleRate: Int = 16000

    @Volatile
    private var frozenAtMillis: Long = 0L

    @Synchronized
    fun set(buffer: ShortArray, rate: Int) {
        frozenBuffer = buffer
        sampleRate = rate
        frozenAtMillis = System.currentTimeMillis()
    }

    @Synchronized
    fun getLatest(): ShortArray? = frozenBuffer

    @Synchronized
    fun getSampleRate(): Int = sampleRate

    @Synchronized
    fun getFrozenAtMillis(): Long = frozenAtMillis

    @Synchronized
    fun clear() {
        frozenBuffer = null
        sampleRate = 16000
        frozenAtMillis = 0L
    }
}
