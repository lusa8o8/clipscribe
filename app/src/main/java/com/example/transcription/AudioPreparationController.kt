package com.example.transcription

import com.example.capture.FrozenAudioBufferHolder
import com.example.core.Constants

object AudioPreparationController {
    fun prepareLatestFrozenAudio(includeWavBytes: Boolean = true): AudioPreparationResult {
        AudioPreparationStateHolder.markPreparing()
        try {
            val samples = FrozenAudioBufferHolder.getLatest()
            val sampleRate = FrozenAudioBufferHolder.getSampleRate()

            if (samples == null || samples.isEmpty()) {
                AudioPreparationStateHolder.markNoFrozenBuffer()
                return AudioPreparationResult.NO_FROZEN_BUFFER
            }

            val duration = samples.size.toDouble() / sampleRate
            if (duration < Constants.MIN_PREPARED_AUDIO_SECONDS) {
                AudioPreparationStateHolder.reset()
                return AudioPreparationResult.TOO_SHORT
            }

            val preparedAudio = AudioPreprocessor.prepare(samples, sampleRate, includeWavBytes)
            PreparedAudioHolder.set(preparedAudio)
            AudioPreparationStateHolder.markReady(preparedAudio.durationSeconds, preparedAudio.sampleCount)

            return AudioPreparationResult.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            AudioPreparationStateHolder.markError()
            return AudioPreparationResult.ERROR
        }
    }
}
