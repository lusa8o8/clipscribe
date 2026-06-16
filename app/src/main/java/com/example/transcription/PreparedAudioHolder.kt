package com.example.transcription

object PreparedAudioHolder {
    @Volatile
    private var preparedAudio: PreparedAudio? = null

    @Synchronized
    fun set(audio: PreparedAudio) {
        preparedAudio = audio
    }

    @Synchronized
    fun getLatest(): PreparedAudio? = preparedAudio

    @Synchronized
    fun clear() {
        preparedAudio = null
    }
}
