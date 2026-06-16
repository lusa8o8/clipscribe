package com.example.transcription

import android.content.Context
import com.example.capture.BufferFreezeController
import com.example.capture.BufferFreezeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CaptureToTranscriptController {

    suspend fun captureAndTranscribeRecentAudio(context: Context): CaptureToTranscriptResult = withContext(Dispatchers.Default) {
        try {
            // 1. Freeze audio
            val freezeResult = BufferFreezeController.freezeCurrentBuffer()
            when (freezeResult) {
                BufferFreezeResult.NO_CAPTURE_SERVICE -> return@withContext CaptureToTranscriptResult.NO_CAPTURE_SERVICE
                BufferFreezeResult.NO_BUFFER_AVAILABLE -> return@withContext CaptureToTranscriptResult.NO_BUFFER_AVAILABLE
                BufferFreezeResult.ERROR -> return@withContext CaptureToTranscriptResult.ERROR
                BufferFreezeResult.SUCCESS_SHORT_BUFFER,
                BufferFreezeResult.SUCCESS_FULL_BUFFER -> {
                    // Continue to preparation
                }
            }

            // 2. Prepare audio
            val prepResult = AudioPreparationController.prepareLatestFrozenAudio(includeWavBytes = true)
            when (prepResult) {
                AudioPreparationResult.NO_FROZEN_BUFFER -> return@withContext CaptureToTranscriptResult.NO_PREPARED_AUDIO
                AudioPreparationResult.TOO_SHORT -> return@withContext CaptureToTranscriptResult.AUDIO_TOO_SHORT
                AudioPreparationResult.ERROR -> return@withContext CaptureToTranscriptResult.ERROR
                AudioPreparationResult.SUCCESS -> {
                    // Continue to transcription
                }
            }

            // check engine availability before transcription or rely on it
            if (!TranscriptionController.isTranscriptionAvailable()) {
                return@withContext CaptureToTranscriptResult.ENGINE_NOT_AVAILABLE
            }

            // 3. Transcribe audio
            val txResult = TranscriptionController.transcribePreparedAudio(context)
            when (txResult) {
                TranscriptionResult.SUCCESS -> return@withContext CaptureToTranscriptResult.SUCCESS
                TranscriptionResult.NO_PREPARED_AUDIO -> return@withContext CaptureToTranscriptResult.NO_PREPARED_AUDIO
                TranscriptionResult.MODEL_MISSING -> return@withContext CaptureToTranscriptResult.MODEL_MISSING
                TranscriptionResult.AUTH_REQUIRED -> return@withContext CaptureToTranscriptResult.AUTH_REQUIRED
                TranscriptionResult.QUOTA_EXCEEDED -> return@withContext CaptureToTranscriptResult.QUOTA_EXCEEDED
                TranscriptionResult.ERROR -> {
                    if (!TranscriptionController.isTranscriptionAvailable()) {
                        return@withContext CaptureToTranscriptResult.ENGINE_NOT_AVAILABLE
                    } else {
                        return@withContext CaptureToTranscriptResult.TRANSCRIPTION_ERROR
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext CaptureToTranscriptResult.ERROR
        }
    }
}
