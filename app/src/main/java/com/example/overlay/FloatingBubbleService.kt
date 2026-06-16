package com.example.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: FloatingBubbleView? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Return START_NOT_STICKY as requested
        showBubble()
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private fun showBubble() {
        if (bubbleView != null) return // Already showing

        // Safety permission check
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val wm = windowManager ?: return

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val bubbleSize = (64 * density).toInt()

        // Default position: Right edge, vertically centered
        val defaultX = displayMetrics.widthPixels - bubbleSize
        val defaultY = (displayMetrics.heightPixels - bubbleSize) / 2

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultX
            y = defaultY
        }

        val view = FloatingBubbleView(
            context = this,
            windowManager = wm,
            params = params,
            onStopService = {
                stopSelf()
            }
        )

        try {
            wm.addView(view, params)
            bubbleView = view
            FloatingBubbleStateHolder.isRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun hideBubble() {
        val wm = windowManager
        val view = bubbleView
        if (wm != null && view != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bubbleView = null
    }

    override fun onDestroy() {
        hideBubble()
        FloatingBubbleStateHolder.isRunning = false
        super.onDestroy()
    }
}
