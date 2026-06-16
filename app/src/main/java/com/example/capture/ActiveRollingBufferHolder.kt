package com.example.capture

object ActiveRollingBufferHolder {
    @Volatile
    private var activeBuffer: RollingAudioBuffer? = null

    @Synchronized
    fun set(buffer: RollingAudioBuffer) {
        activeBuffer = buffer
    }

    @Synchronized
    fun get(): RollingAudioBuffer? = activeBuffer

    @Synchronized
    fun clear() {
        activeBuffer = null
    }
}
