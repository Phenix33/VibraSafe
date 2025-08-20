package com.usiu.finalproject.utilities

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.usiu.finalproject.data.AlertLevel
import com.usiu.finalproject.data.Feedback

class FeedbackManager(private val activity: AppCompatActivity) {

    private val context: Context = activity.baseContext
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var currentAnimation: ValueAnimator? = null

    fun provideFeedback(feedback: Feedback, targetView: View) {
        val alertLevel = AlertLevel.fromString(feedback.alert_level)

        // Provide visual feedback
        provideVisualFeedback(feedback.color, alertLevel, targetView)

        // Provide haptic feedback
        provideHapticFeedback(feedback.vibration_pattern, alertLevel)

        // Keep screen on for critical alerts
        if (alertLevel == AlertLevel.CRITICAL) {
            keepScreenOn(true)
            // Turn off after 10 seconds
            targetView.postDelayed({ keepScreenOn(false) }, 10000)
        }
    }

    private fun provideVisualFeedback(colorHex: String, alertLevel: AlertLevel, targetView: View) {
        try {
            val color = Color.parseColor(colorHex)
            val originalColor = Color.WHITE

            // Cancel any existing animation
            currentAnimation?.cancel()

            // Determine animation duration and repeat count based on alert level
            val (duration, repeatCount) = when (alertLevel) {
                AlertLevel.CRITICAL -> Pair(300L, 6)
                AlertLevel.HIGH -> Pair(400L, 4)
                AlertLevel.MEDIUM -> Pair(500L, 3)
                AlertLevel.LOW -> Pair(600L, 2)
            }

            currentAnimation = ValueAnimator.ofObject(ArgbEvaluator(), originalColor, color, originalColor).apply {
                this.duration = duration
                this.repeatCount = repeatCount

                addUpdateListener { animator ->
                    val animatedColor = animator.animatedValue as Int
                    targetView.setBackgroundColor(animatedColor)
                }

                // Also flash status bar for critical alerts
                if (alertLevel == AlertLevel.CRITICAL || alertLevel == AlertLevel.HIGH) {
                    addUpdateListener { animator ->
                        val animatedColor = animator.animatedValue as Int
                        targetView.setBackgroundColor(animatedColor)
                        flashStatusBar(animatedColor)
                    }
                }

                start()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple color flash
            flashSimple(targetView, alertLevel)
        }
    }

    private fun provideHapticFeedback(pattern: List<Long>, alertLevel: AlertLevel) {
        if (!vibrator.hasVibrator()) return

        try {
            val vibrationPattern = pattern.toLongArray()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = when (alertLevel) {
                    AlertLevel.CRITICAL -> VibrationEffect.DEFAULT_AMPLITUDE
                    AlertLevel.HIGH -> 200
                    AlertLevel.MEDIUM -> 150
                    AlertLevel.LOW -> 100
                }

                val effect = VibrationEffect.createWaveform(vibrationPattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback vibration
            fallbackVibration(alertLevel)
        }
    }

    private fun flashStatusBar(color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = color
        }
    }

    private fun flashSimple(view: View, alertLevel: AlertLevel) {
        val originalColor = Color.WHITE
        val flashColor = when (alertLevel) {
            AlertLevel.CRITICAL -> Color.RED
            AlertLevel.HIGH -> Color.parseColor("#E91E63")
            AlertLevel.MEDIUM -> Color.parseColor("#FF9800")
            AlertLevel.LOW -> Color.parseColor("#4CAF50")
        }

        view.setBackgroundColor(flashColor)
        view.postDelayed({ view.setBackgroundColor(originalColor) }, 500)
    }

    private fun fallbackVibration(alertLevel: AlertLevel) {
        val duration = when (alertLevel) {
            AlertLevel.CRITICAL -> 1000L
            AlertLevel.HIGH -> 800L
            AlertLevel.MEDIUM -> 500L
            AlertLevel.LOW -> 300L
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun keepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun cancelAllFeedback() {
        currentAnimation?.cancel()
        currentAnimation = null

        // Reset status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = Color.BLACK
        }

        keepScreenOn(false)
    }
}
