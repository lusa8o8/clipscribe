package com.example.capture

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.core.DebugFileLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.Q)
class AudioPlaybackCaptureClient(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val rollingBuffer: RollingAudioBuffer
) {
    companion object {
        const val CAPTURE_SAMPLE_RATE = 16000
        const val NO_AUDIO_TIMEOUT_MS = 5000L
        const val AUDIO_DETECTION_RMS_THRESHOLD = 0.01
    }

    private var audioRecord: AudioRecord? = null
    private val isCapturing = AtomicBoolean(false)
    private var captureJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @SuppressLint("MissingPermission")
    fun startCapture() {
        DebugFileLog.write(context, "AudioPlaybackCaptureClient.startCapture begin")
        if (isCapturing.get() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            DebugFileLog.write(context, "AudioPlaybackCaptureClient.startCapture ignored")
            return
        }

        AudioCaptureStateHolder.markInitializing()
        AudioLevelStateHolder.reset()

        try {
            DebugFileLog.write(context, "Building AudioPlaybackCaptureConfiguration")
            // Build AudioPlaybackCaptureConfiguration
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            DebugFileLog.write(context, "Building AudioFormat")
            // Build AudioFormat
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(CAPTURE_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            // Calculate min buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                CAPTURE_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096
            DebugFileLog.write(context, "AudioRecord minBufferSize=$minBufferSize bufferSize=$bufferSize")

            // Build AudioRecord
            DebugFileLog.write(context, "Building AudioRecord")
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            val record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                DebugFileLog.write(context, "AudioRecord not initialized")
                AudioCaptureStateHolder.markError()
                return
            }

            DebugFileLog.write(context, "AudioRecord.startRecording begin")
            record.startRecording()
            DebugFileLog.write(context, "AudioRecord.startRecording success")
            isCapturing.set(true)
            AudioCaptureStateHolder.markCapturing()

            captureJob = coroutineScope.launch {
                val shortBuffer = ShortArray(1024)
                val startTime = System.currentTimeMillis()
                var audioDetectedThisSession = false

                while (isCapturing.get() && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readResult = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readResult > 0) {
                        rollingBuffer.append(shortBuffer, readResult)
                        RollingBufferStateHolder.updateDuration(rollingBuffer.getDurationSeconds())

                        val rms = AudioLevelAnalyzer.calculateRms(shortBuffer, readResult)
                        AudioLevelStateHolder.setRmsLevel(rms)

                        if (AudioLevelAnalyzer.hasMeaningfulAudio(rms)) {
                            audioDetectedThisSession = true
                            AudioCaptureStateHolder.markAudioDetected()
                        } else {
                            // If audio has not been detected yet, and 5 seconds has passed, mark NO_AUDIO_DETECTED
                            if (!audioDetectedThisSession && (System.currentTimeMillis() - startTime >= NO_AUDIO_TIMEOUT_MS)) {
                                AudioCaptureStateHolder.markNoAudioDetected()
                            }
                        }
                    } else if (readResult < 0) {
                        // Error during read
                        if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                            AudioCaptureStateHolder.markBlockedOrUnsupported()
                        } else {
                            AudioCaptureStateHolder.markError()
                        }
                        break
                    }
                    delay(50) // Non-blocking check interval
                }
            }
        } catch (e: SecurityException) {
            DebugFileLog.write(context, "AudioPlaybackCaptureClient security failure", e)
            e.printStackTrace()
            AudioCaptureStateHolder.markBlockedOrUnsupported()
        } catch (e: Throwable) {
            DebugFileLog.write(context, "AudioPlaybackCaptureClient failure", e)
            e.printStackTrace()
            AudioCaptureStateHolder.markError()
        }
    }

    fun stopCapture() {
        if (!isCapturing.getAndSet(false)) {
            return
        }
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
            AudioCaptureStateHolder.markStopped()
            AudioLevelStateHolder.reset()
        }
    }
}
