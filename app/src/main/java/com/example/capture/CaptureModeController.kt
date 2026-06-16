package com.example.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.core.DebugFileLog
import com.example.overlay.FloatingBubbleService

object CaptureModeController {

    fun startAfterConsent(context: Context, resultCode: Int, data: Intent?): Boolean {
        try {
            DebugFileLog.write(context, "CaptureModeController.startAfterConsent resultCode=$resultCode hasData=${data != null}")
            CaptureModeStateHolder.markStarting()

            // 1. Start AudioCaptureService with MediaProjection intent extras
            val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_START_CAPTURE_SERVICE
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                if (data != null) {
                    putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 2. Start FloatingBubbleService if overlay permission is granted
            if (Settings.canDrawOverlays(context)) {
                val bubbleIntent = Intent(context, FloatingBubbleService::class.java)
                context.startService(bubbleIntent)
            }

            CaptureModeStateHolder.markActive()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            CaptureModeStateHolder.markError()
            return false
        }
    }

    fun stopCaptureMode(context: Context) {
        try {
            CaptureModeStateHolder.markStopping()

            // Stop AudioCaptureService
            val stopCaptureIntent = Intent(context, AudioCaptureService::class.java).apply {
                action = AudioCaptureService.ACTION_STOP_CAPTURE_SERVICE
            }
            context.startService(stopCaptureIntent)
            context.stopService(Intent(context, AudioCaptureService::class.java))

            // Stop FloatingBubbleService
            context.stopService(Intent(context, FloatingBubbleService::class.java))

            // Clear or release active rolling buffer holder
            ActiveRollingBufferHolder.clear()
            FrozenAudioBufferHolder.clear()

            CaptureModeStateHolder.markOff()
        } catch (e: Exception) {
            e.printStackTrace()
            CaptureModeStateHolder.markError()
        }
    }
}
