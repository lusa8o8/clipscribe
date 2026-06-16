package com.example.capture

import com.example.core.Constants

object BufferFreezeController {
    fun freezeCurrentBuffer(): BufferFreezeResult {
        try {
            // Check capture service state
            if (CaptureServiceStateHolder.currentState() != CaptureServiceState.ACTIVE) {
                return BufferFreezeResult.NO_CAPTURE_SERVICE
            }

            // Retrieve active rolling buffer
            val activeBuffer = ActiveRollingBufferHolder.get() ?: return BufferFreezeResult.NO_BUFFER_AVAILABLE

            // Get a snapshot of current samples
            val snapshot = activeBuffer.snapshot()
            if (snapshot.isEmpty()) {
                return BufferFreezeResult.NO_BUFFER_AVAILABLE
            }

            // Store in FrozenAudioBufferHolder
            val sampleRate = AudioPlaybackCaptureClient.CAPTURE_SAMPLE_RATE
            FrozenAudioBufferHolder.set(snapshot, sampleRate)

            // Mark frozen state with sample count
            RollingBufferStateHolder.markFrozen(snapshot.size)

            val duration = snapshot.size.toDouble() / sampleRate
            return if (duration < Constants.DEFAULT_BUFFER_SECONDS) {
                BufferFreezeResult.SUCCESS_SHORT_BUFFER
            } else {
                BufferFreezeResult.SUCCESS_FULL_BUFFER
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RollingBufferStateHolder.markError()
            return BufferFreezeResult.ERROR
        }
    }
}
