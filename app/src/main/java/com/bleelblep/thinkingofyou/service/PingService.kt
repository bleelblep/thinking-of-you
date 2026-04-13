package com.bleelblep.thinkingofyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.bleelblep.thinkingofyou.MainActivity
import com.bleelblep.thinkingofyou.R
import com.bleelblep.thinkingofyou.data.HistoryEntry
import com.bleelblep.thinkingofyou.data.HistoryStore
import com.bleelblep.thinkingofyou.data.ThinkingOfYouPreferences
import com.bleelblep.thinkingofyou.firebase.FirebaseAuthClient
import com.bleelblep.thinkingofyou.firebase.FirebaseRestClient
import com.bleelblep.thinkingofyou.glyph.GlyphAnimationPlayer
import com.bleelblep.thinkingofyou.ui.HeartOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that listens for pings via Firebase SSE and plays Glyph animations.
 */
class PingService : Service() {

    companion object {
        private const val TAG = "PingService"
        private const val NOTIFICATION_CHANNEL_ID = "thinking_of_you_channel"
        private const val PING_NOTIFICATION_CHANNEL_ID = "thinking_of_you_ping_channel"
        private const val NOTIFICATION_ID = 9001
        private const val PING_NOTIFICATION_ID = 9002
        const val PING_RECEIVED_ACTION = "com.bleelblep.thinkingofyou.PING_RECEIVED"
        const val STOP_ANIMATION_ACTION = "com.bleelblep.thinkingofyou.STOP_ANIMATION"

        const val ACTION_START = "com.bleelblep.thinkingofyou.ACTION_START_PING_LISTENER"
        const val ACTION_STOP = "com.bleelblep.thinkingofyou.ACTION_STOP_PING_LISTENER"
        const val ACTION_SEND_PING = "com.bleelblep.thinkingofyou.ACTION_SEND_PING"
        const val ACTION_STOP_ANIMATION = "com.bleelblep.thinkingofyou.ACTION_STOP_ANIMATION"

        fun start(context: Context) {
            val intent = Intent(context, PingService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun sendPing(context: Context) {
            val intent = Intent(context, PingService::class.java).apply {
                action = ACTION_SEND_PING
            }
            context.startService(intent)
        }

        fun stopAnimation(context: Context) {
            val intent = Intent(context, PingService::class.java).apply {
                action = ACTION_STOP_ANIMATION
            }
            context.startService(intent)
        }
    }

    private var authClient: FirebaseAuthClient? = null
    private var restClient: FirebaseRestClient? = null
    private var animationPlayer: GlyphAnimationPlayer? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        authClient = FirebaseAuthClient(this)
        restClient = FirebaseRestClient(this, authClient!!)
        animationPlayer = GlyphAnimationPlayer(this)
        animationPlayer?.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createSilentNotification())
                startListening()
            }
            ACTION_STOP -> stopListening()
            ACTION_SEND_PING -> handleSendPing()
            ACTION_STOP_ANIMATION -> handleStopAnimation()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        restClient?.stopPingListener()
        animationPlayer?.uninitialize()
        coroutineScope.cancel()
        super.onDestroy()
    }

    private fun startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }

        if (!ThinkingOfYouPreferences.isFeatureEnabled(this)) {
            Log.d(TAG, "Feature disabled, not starting listener")
            stopSelf()
            return
        }

        if (!ThinkingOfYouPreferences.isPaired(this)) {
            Log.d(TAG, "Not paired, cannot start listening")
            stopSelf()
            return
        }

        coroutineScope.launch {
            try {
                val tokenResult = authClient?.initializeAuth()
                if (tokenResult?.isFailure == true) {
                    Log.e(TAG, "Auth initialization failed")
                    stopListening()
                    return@launch
                }

                val pairCode = ThinkingOfYouPreferences.getPairCode(this@PingService)
                val uid = ThinkingOfYouPreferences.getFirebaseUid(this@PingService)
                val deviceSlot = ThinkingOfYouPreferences.getDeviceSlot(this@PingService)

                if (pairCode == null || uid == null || deviceSlot == null) {
                    Log.e(TAG, "Missing pairing data")
                    stopListening()
                    return@launch
                }

                restClient?.updatePresence(pairCode, deviceSlot, true)

                restClient?.startPingListener(
                    pairCode = pairCode,
                    ownUid = uid,
                    onPingReceived = { pingData ->
                        handlePingReceived(pingData)
                    },
                    onConnectionLost = {
                        Log.w(TAG, "SSE connection lost")
                    }
                )

                isListening = true
                Log.d(TAG, "Listening for pings")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting listener", e)
                stopListening()
            }
        }
    }

    private fun stopListening() {
        Log.d(TAG, "Stopping listener")

        coroutineScope.launch {
            try {
                val pairCode = ThinkingOfYouPreferences.getPairCode(this@PingService)
                val deviceSlot = ThinkingOfYouPreferences.getDeviceSlot(this@PingService)

                if (pairCode != null && deviceSlot != null) {
                    restClient?.updatePresence(pairCode, deviceSlot, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating offline status", e)
            }
        }

        restClient?.stopPingListener()
        isListening = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private var currentPingAnimationType: String = ThinkingOfYouPreferences.ANIMATION_HEART
    private var currentPingCustomText: String? = null

    private fun handlePingReceived(pingData: FirebaseRestClient.PingData) {
        Log.d(TAG, "Ping received at ${pingData.sentAt}, animation=${pingData.animationType}")

        currentPingAnimationType = pingData.animationType
        currentPingCustomText = pingData.customText

        ThinkingOfYouPreferences.setUnreadPing(this, true)
        ThinkingOfYouPreferences.setLastPingReceivedAt(this, pingData.sentAt)

        coroutineScope.launch {
            try {
                val pairCode = ThinkingOfYouPreferences.getPairCode(this@PingService)
                if (pairCode != null) {
                    restClient?.markDelivered(pairCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking delivered", e)
            }
        }

        val currentTime = System.currentTimeMillis()
        val deliveryMode = if (currentTime - pingData.sentAt <= 10_000) "live" else "delayed"

        playReceiveAnimation()
        showHeartOverlay()
        playReceiveSound()
        playReceiveVibration()

        val isLooping = ThinkingOfYouPreferences.isLoopAnimationEnabled(this)
        showPingReceivedNotification(isLooping)

        HistoryStore.addEntry(
            this,
            HistoryEntry(
                type = "received",
                timestamp = currentTime,
                deliveryMode = deliveryMode
            )
        )

        sendBroadcast(Intent(PING_RECEIVED_ACTION))
    }

    private fun showHeartOverlay() {
        if (!ThinkingOfYouPreferences.isScreenOverlayEnabled(this)) return
        try {
            val intent = Intent(this, HeartOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(HeartOverlayActivity.EXTRA_LOOP, ThinkingOfYouPreferences.isLoopAnimationEnabled(this@PingService))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing heart overlay", e)
        }
    }

    private fun handleSendPing() {
        if (!ThinkingOfYouPreferences.isPaired(this)) {
            Log.d(TAG, "Not paired, cannot send ping")
            return
        }

        triggerHaptic()
        playSendAnimation()

        HistoryStore.addEntry(
            this,
            HistoryEntry(
                type = "sent",
                timestamp = System.currentTimeMillis(),
                deliveryMode = "live"
            )
        )

        coroutineScope.launch {
            try {
                val pairCode = ThinkingOfYouPreferences.getPairCode(this@PingService)
                val uid = ThinkingOfYouPreferences.getFirebaseUid(this@PingService)
                val animationType = ThinkingOfYouPreferences.getAnimationType(this@PingService)
                val customText = if (animationType == ThinkingOfYouPreferences.ANIMATION_TEXT) {
                    ThinkingOfYouPreferences.getCustomText(this@PingService)
                } else null

                if (pairCode != null && uid != null) {
                    val result = restClient?.sendPing(
                        pairCode = pairCode,
                        fromUid = uid,
                        animationType = animationType,
                        customText = customText
                    )
                    if (result?.isSuccess == true) {
                        Log.d(TAG, "Ping sent successfully with animation=$animationType")
                    } else {
                        Log.e(TAG, "Failed to send ping")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending ping", e)
            }
        }
    }

    private fun playSendAnimation() {
        val animationType = ThinkingOfYouPreferences.getAnimationType(this)
        val customText = ThinkingOfYouPreferences.getCustomText(this)
        playAnimationByType(
            animationType = animationType,
            customText = customText,
            loop = false
        ) {
            Log.d(TAG, "Send animation complete")
            animationPlayer?.closeAppMatrix()
        }
    }

    private fun playReceiveAnimation() {
        val shouldLoop = ThinkingOfYouPreferences.isLoopAnimationEnabled(this)
        playAnimationByType(
            animationType = currentPingAnimationType,
            customText = currentPingCustomText,
            loop = shouldLoop
        ) {
            Log.d(TAG, "Receive animation complete")
            animationPlayer?.closeAppMatrix()
        }
    }

    private fun playAnimationByType(
        animationType: String,
        customText: String?,
        loop: Boolean,
        onComplete: () -> Unit
    ) {
        when (animationType) {
            ThinkingOfYouPreferences.ANIMATION_HEART -> {
                animationPlayer?.playAnimatedHeart(loop = loop, onComplete = onComplete)
            }
            ThinkingOfYouPreferences.ANIMATION_CONFETTI -> {
                animationPlayer?.playConfetti(loop = loop, onComplete = onComplete)
            }
            ThinkingOfYouPreferences.ANIMATION_TEXT -> {
                val text = customText ?: "HI"
                animationPlayer?.playTextScroll(text = text, loop = loop, onComplete = onComplete)
            }
            else -> {
                animationPlayer?.playAnimatedHeart(loop = loop, onComplete = onComplete)
            }
        }
    }

    private fun handleStopAnimation() {
        Log.d(TAG, "Stopping animation")
        animationPlayer?.stopLooping()
        animationPlayer?.stopAnimation()
        animationPlayer?.closeAppMatrix()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(PING_NOTIFICATION_ID)

        sendBroadcast(Intent(STOP_ANIMATION_ACTION))
    }

    private fun triggerHaptic() {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering haptic", e)
        }
    }

    private fun playReceiveSound() {
        if (!ThinkingOfYouPreferences.isSoundEnabled(this)) return
        var mediaPlayer: MediaPlayer? = null
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.thinking_of_you_receive)
            if (mediaPlayer == null) {
                Log.e(TAG, "Failed to create MediaPlayer")
                return
            }
            mediaPlayer.setOnCompletionListener { mp ->
                try {
                    mp.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing MediaPlayer on completion", e)
                }
            }
            mediaPlayer.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                try {
                    mp.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing MediaPlayer on error", e)
                }
                true
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing receive sound", e)
            try {
                mediaPlayer?.release()
            } catch (releaseEx: Exception) {
                Log.w(TAG, "Error releasing MediaPlayer after exception", releaseEx)
            }
        }
    }

    private fun playReceiveVibration() {
        if (!ThinkingOfYouPreferences.isVibrationEnabled(this)) return
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 150, 100, 150), -1
            )
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing receive vibration", e)
        }
    }

    private fun createSilentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Thinking of You")
            .setContentText("Listening for pings...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val listenerChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Thinking of You",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background listener for pings"
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(listenerChannel)

        val pingChannel = NotificationChannel(
            PING_NOTIFICATION_CHANNEL_ID,
            "Thinking of You - Ping Received",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when you receive a ping"
            setShowBadge(true)
        }
        manager.createNotificationChannel(pingChannel)
    }

    private fun showPingReceivedNotification(isLooping: Boolean) {
        val overlayIntent = Intent(this, HeartOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(HeartOverlayActivity.EXTRA_LOOP, isLooping)
        }
        val overlayPendingIntent = PendingIntent.getActivity(
            this, 0, overlayIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = Notification.Builder(this, PING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Someone is thinking of you!")
            .setContentText("Tap to see the animation")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(overlayPendingIntent)
            .setAutoCancel(true)

        if (isLooping) {
            val stopIntent = Intent(this, PingService::class.java).apply {
                action = ACTION_STOP_ANIMATION
            }
            val stopPendingIntent = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationBuilder
                .setContentText("Tap to see, or Dismiss to stop")
                .addAction(
                    Notification.Action.Builder(
                        null,
                        "Dismiss",
                        stopPendingIntent
                    ).build()
                )
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(PING_NOTIFICATION_ID, notificationBuilder.build())
    }
}
