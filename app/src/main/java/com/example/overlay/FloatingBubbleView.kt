package com.example.overlay

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onStopService: () -> Unit
) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isLongPressed = false

    private val viewScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    private var isProcessingTap = false

    // State tracks whether the menu is expanded
    private var isMenuExpanded = false

    private var currentBubbleState: BubbleState = BubbleState.IDLE

    private lateinit var logoImageView: ImageView
    private lateinit var ringImageView: ImageView
    private var rotationAnimator: ObjectAnimator? = null

    private fun updateBubbleState(state: BubbleState) {
        currentBubbleState = state
        handler.post {
            val bg = bubbleCircle.background as? GradientDrawable ?: GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).toInt(), Color.WHITE)
            }

            when (state) {
                BubbleState.IDLE -> {
                    bg.setColor(0xFF2C3E50.toInt()) // Slate Primary
                    ringImageView.visibility = View.GONE
                    rotationAnimator?.cancel()
                }
                BubbleState.PRESSED -> {
                    bg.setColor(0xFF1A252F.toInt()) // Dark slate
                    ringImageView.visibility = View.GONE
                    rotationAnimator?.cancel()
                }
                BubbleState.PROCESSING -> {
                    bg.setColor(0xFF3498DB.toInt()) // Soft Blue
                    ringImageView.visibility = View.VISIBLE
                    if (rotationAnimator?.isRunning != true) {
                        rotationAnimator = ObjectAnimator.ofFloat(ringImageView, View.ROTATION, 0f, 360f).apply {
                            duration = 900
                            repeatCount = ObjectAnimator.INFINITE
                            interpolator = LinearInterpolator()
                            start()
                        }
                    }
                }
                BubbleState.SUCCESS -> {
                    bg.setColor(0xFF2ECC71.toInt()) // Green
                    ringImageView.visibility = View.GONE
                    rotationAnimator?.cancel()
                }
                BubbleState.ERROR -> {
                    bg.setColor(0xFFE74C3C.toInt()) // Elegant Red
                    ringImageView.visibility = View.GONE
                    rotationAnimator?.cancel()
                }
            }
            bubbleCircle.background = bg
        }
    }

    private val bubbleSizePx: Int
    private val density: Float

    // Subviews
    private val bubbleCircle: FrameLayout
    private val menuLayout: LinearLayout

    private val longPressRunnable = Runnable {
        isLongPressed = true
        showLongPressMenu()
    }

    init {
        density = context.resources.displayMetrics.density
        bubbleSizePx = (64 * density).toInt()

        // Configure itself to allow drawing translucent backgrounds
        setBackgroundColor(Color.TRANSPARENT)

        // Spinning ring overlay (PROCESSING state only)
        ringImageView = ImageView(context).apply {
            setImageResource(R.drawable.ic_processing_ring)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }

        // Waveform logo icon (always visible)
        val logoPx = (28 * density).toInt()
        logoImageView = ImageView(context).apply {
            setImageResource(R.drawable.ic_cs_bubble)
            layoutParams = LayoutParams(logoPx, logoPx, Gravity.CENTER)
        }

        // Circle subview
        bubbleCircle = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF2C3E50.toInt()) // Slate Primary
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            background = bg
            elevation = 6 * density

            addView(ringImageView)
            addView(logoImageView)
        }

        // Horizontal pill menu subview
        menuLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 32 * density // rounded pill look
                setColor(0xFF1A252F.toInt()) // dark contrasting slate
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            background = bg
            elevation = 8 * density
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)

            // Mini header/title icon inside pill menu
            addView(TextView(context).apply {
                text = "🎙️"
                textSize = 18f
                setPadding(0, 0, (8 * density).toInt(), 0)
                setOnClickListener {
                    collapseMenu()
                }
            })

            // Action Button 1: Open ClipScribe
            addView(TextView(context).apply {
                text = "Open ClipScribe"
                setTextColor(Color.WHITE)
                textSize = 13f
                paint.isFakeBoldText = true
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                setOnClickListener {
                    openApp()
                    collapseMenu()
                }
            })

            // Separator
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), (20 * density).toInt()).apply {
                    setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }
                setBackgroundColor(0xFF555555.toInt())
            })

            // Action Button 2: Stop Bubble
            addView(TextView(context).apply {
                text = "Stop Bubble"
                setTextColor(0xFFFF4D4D.toInt()) // soft red
                textSize = 13f
                paint.isFakeBoldText = true
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                setOnClickListener {
                    onStopService()
                }
            })
        }

        // Add default circle view first
        addView(bubbleCircle, LayoutParams(bubbleSizePx, bubbleSizePx, Gravity.CENTER))
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept touch events to enable custom dragging
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (currentBubbleState == BubbleState.PROCESSING || isProcessingTap) {
                    return false
                }
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isLongPressed = false

                if (!isMenuExpanded) {
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    updateBubbleState(BubbleState.PRESSED)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY

                if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                    isDragging = true
                    handler.removeCallbacks(longPressRunnable)
                }

                if (isDragging) {
                    params.x = initialX + deltaX.toInt()
                    params.y = initialY + deltaY.toInt()

                    // Ensure bubble stays bounded to screen roughly
                    val metrics = context.resources.displayMetrics
                    if (params.x < 0) params.x = 0
                    if (params.x > metrics.widthPixels - this.width) params.x = metrics.widthPixels - this.width
                    if (params.y < 0) params.y = 0
                    if (params.y > metrics.heightPixels - this.height) params.y = metrics.heightPixels - this.height

                    windowManager.updateViewLayout(this, params)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (!isDragging && !isLongPressed) {
                    if (isMenuExpanded) {
                        collapseMenu()
                    } else {
                        if (isProcessingTap || currentBubbleState == BubbleState.PROCESSING) {
                            return true
                        }
                        isProcessingTap = true
                        updateBubbleState(BubbleState.PROCESSING)

                        viewScope.launch {
                            try {
                                val result = com.example.transcription.CaptureToTranscriptController.captureAndTranscribeRecentAudio(context)
                                if (result == com.example.transcription.CaptureToTranscriptResult.SUCCESS) {
                                    val text = com.example.transcription.TranscriptionResultHolder.latestText.value
                                    val durationMs = com.example.transcription.TranscriptionResultHolder.durationMillis.value
                                    val freeTierDailyRemaining = com.example.transcription.TranscriptionResultHolder.freeTierUsage.value.dailyRemaining
                                    val sourceDurationSec = com.example.transcription.PreparedAudioHolder.getLatest()?.durationSeconds

                                    com.example.ui.result.TranscriptResultStateHolder.showSuccess(
                                        text = text,
                                        durationMillis = durationMs,
                                        sourceDurationSeconds = sourceDurationSec,
                                        freeTierDailyRemaining = freeTierDailyRemaining
                                    )

                                    updateBubbleState(BubbleState.SUCCESS)
                                    Toast.makeText(context, "Transcript ready.", Toast.LENGTH_LONG).show()

                                    openApp()
                                } else {
                                    val message = when (result) {
                                        com.example.transcription.CaptureToTranscriptResult.NO_CAPTURE_SERVICE -> "Start capture mode first."
                                        com.example.transcription.CaptureToTranscriptResult.NO_BUFFER_AVAILABLE -> "No recent audio buffer available yet."
                                        com.example.transcription.CaptureToTranscriptResult.AUDIO_TOO_SHORT -> "Recent audio is too short to transcribe."
                                        com.example.transcription.CaptureToTranscriptResult.NO_PREPARED_AUDIO -> "No recent audio buffer available yet."
                                        com.example.transcription.CaptureToTranscriptResult.MODEL_MISSING -> "Local transcription model not found."
                                        com.example.transcription.CaptureToTranscriptResult.AUTH_REQUIRED -> "Authentication expired. Reopen ClipScribe and try again."
                                        com.example.transcription.CaptureToTranscriptResult.QUOTA_EXCEEDED -> "Free transcript limit reached for today."
                                        com.example.transcription.CaptureToTranscriptResult.ENGINE_NOT_AVAILABLE -> "Local transcription engine is not available on this build."
                                        com.example.transcription.CaptureToTranscriptResult.TRANSCRIPTION_ERROR -> "Could not transcribe recent audio."
                                        else -> "Something went wrong while capturing recent audio."
                                    }

                                    com.example.ui.result.TranscriptResultStateHolder.showError(message)
                                    updateBubbleState(BubbleState.ERROR)
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                                    openApp()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                com.example.ui.result.TranscriptResultStateHolder.showError("Something went wrong while capturing recent audio.")
                                updateBubbleState(BubbleState.ERROR)
                                openApp()
                            } finally {
                                handler.postDelayed({
                                    updateBubbleState(BubbleState.IDLE)
                                    isProcessingTap = false
                                }, 1500)
                            }
                        }
                    }
                } else {
                    if (currentBubbleState == BubbleState.PRESSED) {
                        updateBubbleState(BubbleState.IDLE)
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun showLongPressMenu() {
        if (isMenuExpanded) return
        isMenuExpanded = true

        // Remove the circular view, render the horizontal menu pill instead
        removeAllViews()

        val wrapContent = LayoutParams.WRAP_CONTENT
        val pillHeight = (56 * density).toInt()
        addView(menuLayout, LayoutParams(wrapContent, pillHeight, Gravity.CENTER))

        // Update layouts to wrap content size dynamically
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = pillHeight
        windowManager.updateViewLayout(this, params)
    }

    private fun collapseMenu() {
        if (!isMenuExpanded) return
        isMenuExpanded = false

        removeAllViews()
        addView(bubbleCircle, LayoutParams(bubbleSizePx, bubbleSizePx, Gravity.CENTER))

        params.width = bubbleSizePx
        params.height = bubbleSizePx
        windowManager.updateViewLayout(this, params)
    }

    private fun openApp() {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(launchIntent)
    }
}
