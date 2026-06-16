package com.example.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.core.DebugFileLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        const val ACTION_START_CAPTURE_SERVICE = "com.example.action.START_CAPTURE_SERVICE"
        const val ACTION_STOP_CAPTURE_SERVICE = "com.example.action.STOP_CAPTURE_SERVICE"

        private const val CHANNEL_ID = "clipscribe_capture_service"
        private const val NOTIFICATION_ID = 101

        @Volatile
        var activeRollingBuffer: RollingAudioBuffer? = null
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var playbackCaptureClient: AudioPlaybackCaptureClient? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        DebugFileLog.write(this, "AudioCaptureService.onStartCommand action=$action")
        if (action == ACTION_STOP_CAPTURE_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_CAPTURE_SERVICE) {
            startCaptureService(intent)
        }
        return START_NOT_STICKY
    }

    private fun startCaptureService(intent: Intent) {
        DebugFileLog.write(this, "startCaptureService begin")
        CaptureServiceStateHolder.markStarting()

        val missingResultCode = Int.MIN_VALUE
        var resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, missingResultCode)
        var resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        }

        if (resultCode == missingResultCode || resultData == null) {
            val fallbackResultCode = MediaProjectionConsentStateHolder.getLatestResultCode()
            val fallbackData = MediaProjectionConsentStateHolder.getLatestData()
            if (fallbackResultCode != null && fallbackData != null) {
                DebugFileLog.write(this, "using MediaProjection result fallback from state holder")
                resultCode = fallbackResultCode
                resultData = fallbackData
            }
        }

        if (resultCode == missingResultCode || resultData == null) {
            DebugFileLog.write(this, "startCaptureService missing MediaProjection result data")
            CaptureServiceStateHolder.markError()
            stopSelf()
            return
        }

        createNotificationChannel()
        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            DebugFileLog.write(this, "startForeground success")
            CaptureServiceStateHolder.markActive()
        } catch (e: Throwable) {
            DebugFileLog.write(this, "startForeground failed", e)
            e.printStackTrace()
            CaptureServiceStateHolder.markError()
            stopSelf()
            return
        }

        // Start capture logic after foreground notification is active
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            serviceScope.launch {
                try {
                    DebugFileLog.write(this@AudioCaptureService, "capture setup begin")
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val mp = projectionManager.getMediaProjection(resultCode, resultData)
                    if (mp == null) {
                        DebugFileLog.write(this@AudioCaptureService, "getMediaProjection returned null")
                        AudioCaptureStateHolder.markError()
                        return@launch
                    }
                    DebugFileLog.write(this@AudioCaptureService, "getMediaProjection success")
                    val callback = object : MediaProjection.Callback() {
                        override fun onStop() {
                            DebugFileLog.write(this@AudioCaptureService, "MediaProjection onStop")
                            AudioCaptureStateHolder.markStopped()
                            stopSelf()
                        }
                    }
                    mp.registerCallback(callback, Handler(Looper.getMainLooper()))
                    mediaProjectionCallback = callback
                    mediaProjection = mp
                    val buffer = RollingAudioBuffer(
                        sampleRate = com.example.core.Constants.TARGET_SAMPLE_RATE,
                        maxDurationSeconds = com.example.core.Constants.DEFAULT_BUFFER_SECONDS
                    )
                    activeRollingBuffer = buffer
                    ActiveRollingBufferHolder.set(buffer)
                    DebugFileLog.write(this@AudioCaptureService, "AudioPlaybackCaptureClient start")
                    playbackCaptureClient = AudioPlaybackCaptureClient(this@AudioCaptureService, mp, buffer).apply {
                        startCapture()
                    }
                    DebugFileLog.write(this@AudioCaptureService, "AudioPlaybackCaptureClient started")
                } catch (e: Throwable) {
                    DebugFileLog.write(this@AudioCaptureService, "capture setup failed", e)
                    e.printStackTrace()
                    AudioCaptureStateHolder.markError()
                }
            }
        } else {
            DebugFileLog.write(this, "playback capture unsupported before Android Q")
            AudioCaptureStateHolder.markBlockedOrUnsupported()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ClipScribe Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when ClipScribe capture mode is active."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = ACTION_STOP_CAPTURE_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val openIntent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            androidx.core.app.NotificationCompat.Builder(this)
        }

        return builder
            .setContentTitle("ClipScribe capture service active")
            .setContentText("Listening for supported playback audio.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setContentIntent(openPendingIntent)
            .build()
    }

    override fun onDestroy() {
        DebugFileLog.write(this, "AudioCaptureService.onDestroy")
        try {
            playbackCaptureClient?.stopCapture()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            playbackCaptureClient = null
        }

        try {
            val callback = mediaProjectionCallback
            if (callback != null) {
                mediaProjection?.unregisterCallback(callback)
            }
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaProjectionCallback = null
            mediaProjection = null
        }

        CaptureServiceStateHolder.markStopped()
        activeRollingBuffer = null
        ActiveRollingBufferHolder.clear()
        RollingBufferStateHolder.clear()
        serviceScope.cancel()
        super.onDestroy()
    }
}
