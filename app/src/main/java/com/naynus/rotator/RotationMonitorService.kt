package com.naynus.rotator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.OrientationEventListener
import android.view.Surface
import androidx.core.app.NotificationCompat

class RotationMonitorService : Service() {

    private lateinit var orientationListener: OrientationEventListener
    private lateinit var overlayButtonController: OverlayButtonController
    private val handler = Handler(Looper.getMainLooper())

    private var pendingBucket = -1
    private var lastHandledBucket = -1
    private var confirmInProgress = false

    private val debounceRunnable = Runnable {
        if (pendingBucket != -1 && pendingBucket != lastHandledBucket) {
            lastHandledBucket = pendingBucket
            showRotatePrompt()
        }
    }

    private val autoDismissRunnable = Runnable {
        overlayButtonController.hide()
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundWithNotification()

        overlayButtonController = OverlayButtonController(this) { onConfirmTapped() }
        lastHandledBucket = currentRotationBucket()

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientationDegrees: Int) {
                if (orientationDegrees == ORIENTATION_UNKNOWN || confirmInProgress) return
                val bucket = bucketFromDegrees(orientationDegrees)
                if (bucket != pendingBucket) {
                    pendingBucket = bucket
                    handler.removeCallbacks(debounceRunnable)
                    handler.postDelayed(debounceRunnable, DEBOUNCE_MS)
                }
            }
        }
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        orientationListener.disable()
        handler.removeCallbacksAndMessages(null)
        overlayButtonController.hide()
        super.onDestroy()
    }

    private fun currentRotationBucket(): Int {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        return when (display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun bucketFromDegrees(degrees: Int): Int = ((degrees + 45) / 90 * 90) % 360

    private fun showRotatePrompt() {
        handler.removeCallbacks(autoDismissRunnable)
        overlayButtonController.show()
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
    }

    private fun onConfirmTapped() {
        handler.removeCallbacks(autoDismissRunnable)
        overlayButtonController.hide()

        if (!Settings.System.canWrite(this) || confirmInProgress) return
        confirmInProgress = true

        val resolver = contentResolver
        val startRotation = currentRotationBucket()
        Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)

        val startTime = System.currentTimeMillis()
        val pollRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val rotated = currentRotationBucket() != startRotation
                if (rotated || elapsed >= CONFIRM_TIMEOUT_MS) {
                    Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                    confirmInProgress = false
                } else {
                    handler.postDelayed(this, POLL_INTERVAL_MS)
                }
            }
        }
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_rotate)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val CHANNEL_ID = "rotation_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val DEBOUNCE_MS = 500L
        private const val AUTO_DISMISS_MS = 6000L
        private const val POLL_INTERVAL_MS = 100L
        private const val CONFIRM_TIMEOUT_MS = 1200L
    }
}
