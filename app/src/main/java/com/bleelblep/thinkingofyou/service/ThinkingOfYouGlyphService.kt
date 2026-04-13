package com.bleelblep.thinkingofyou.service

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bleelblep.thinkingofyou.R
import com.bleelblep.thinkingofyou.data.ThinkingOfYouPreferences
import com.nothing.ketchum.GlyphMatrixManager
import org.json.JSONObject

/**
 * Dedicated Glyph Toy for Thinking of You feature.
 * Long-press the Glyph to send a ping to your partner.
 * When receiving a ping, shows heart animation with optional looping.
 */
class ThinkingOfYouGlyphService : GlyphMatrixService("ThinkingOfYou") {

    companion object {
        private const val TAG = "ThinkingOfYouGlyphService"
        private const val FRAME_RATE_MS = 50L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var currentFrameIndex = 0
    private var animationFrames: List<IntArray> = emptyList()
    private var frameDurations: List<Long> = emptyList()

    private var mediaPlayer: MediaPlayer? = null

    private var isPlayingAnimation = false
    private var isLooping = false
    private var onAnimationComplete: (() -> Unit)? = null

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || !isPlayingAnimation) return

            if (currentFrameIndex >= animationFrames.size) {
                if (isLooping && ThinkingOfYouPreferences.isLoopAnimationEnabled(applicationContext)) {
                    currentFrameIndex = 0
                } else {
                    stopAnimation()
                    onAnimationComplete?.invoke()
                    return
                }
            }

            val frame = animationFrames[currentFrameIndex]
            glyphMatrixManager?.setMatrixFrame(frame)

            val duration = frameDurations.getOrElse(currentFrameIndex) { FRAME_RATE_MS }
            currentFrameIndex++

            handler.postDelayed(this, duration)
        }
    }

    override fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {
        Log.d(TAG, "Service connected")

        loadHeartAnimation()

        if (!ThinkingOfYouPreferences.isPaired(context)) {
            Log.d(TAG, "Not paired - showing idle state")
            showIdleState()
            return
        }

        isRunning = true
        showIdleState()
    }

    override fun performOnServiceDisconnected(context: Context) {
        Log.d(TAG, "Service disconnected")

        isRunning = false
        isPlayingAnimation = false
        handler.removeCallbacks(frameRunnable)

        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onTouchPointPressed() {
        if (isPlayingAnimation && isLooping) {
            isLooping = false
            stopAnimation()
            showIdleState()
        }
    }

    override fun onTouchPointLongPress() {
        val context = applicationContext

        if (!ThinkingOfYouPreferences.isFeatureEnabled(context)) {
            Log.d(TAG, "Feature disabled")
            return
        }

        if (!ThinkingOfYouPreferences.isPaired(context)) {
            Log.d(TAG, "Not paired, cannot send ping")
            return
        }

        Log.d(TAG, "Long press - sending ping!")

        PingService.sendPing(context)

        isLooping = false
        playHeartAnimation {
            showIdleState()
        }
    }

    override fun onTouchPointReleased() {
        // Release - no action
    }

    override fun onAlwaysOnDisplayEvent() {
        showIdleState()
    }

    fun onPingReceived() {
        Log.d(TAG, "Ping received - playing animation")

        val context = applicationContext

        if (ThinkingOfYouPreferences.isSoundEnabled(context)) {
            playReceiveSound()
        }

        isLooping = ThinkingOfYouPreferences.isLoopAnimationEnabled(context)
        playHeartAnimation {
            showIdleState()
        }
    }

    private fun loadHeartAnimation() {
        try {
            val content = assets.open("thinkingofyou/sparkling_heart_anim_2.json")
                .bufferedReader().use { it.readText() }

            val json = JSONObject(content)
            val framesArray = json.getJSONArray("frames")

            val frames = mutableListOf<IntArray>()
            val durations = mutableListOf<Long>()

            for (i in 0 until framesArray.length()) {
                val frameJson = framesArray.getJSONObject(i)
                val durationMs = frameJson.optLong("d", FRAME_RATE_MS)
                val pixelsArray = frameJson.getJSONArray("p")

                val rawPixels = IntArray(pixelsArray.length())
                for (j in 0 until pixelsArray.length()) {
                    rawPixels[j] = pixelsArray.getInt(j)
                }

                val gridPixels = diamond489ToGrid625(rawPixels)
                val scaledPixels = IntArray(625)
                for (j in 0 until 625) {
                    scaledPixels[j] = (gridPixels[j] * 2047) / 255
                }

                frames.add(scaledPixels)
                durations.add(durationMs)
            }

            animationFrames = frames
            frameDurations = durations
            Log.d(TAG, "Loaded ${frames.size} animation frames")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading animation", e)
            animationFrames = emptyList()
            frameDurations = emptyList()
        }
    }

    private fun playHeartAnimation(onComplete: (() -> Unit)? = null) {
        if (animationFrames.isEmpty()) {
            Log.w(TAG, "No animation frames loaded")
            onComplete?.invoke()
            return
        }

        stopAnimation()

        currentFrameIndex = 0
        isPlayingAnimation = true
        onAnimationComplete = onComplete

        handler.post(frameRunnable)
    }

    private fun stopAnimation() {
        isPlayingAnimation = false
        handler.removeCallbacks(frameRunnable)
        currentFrameIndex = 0
        onAnimationComplete = null
    }

    private fun showIdleState() {
        if (!ThinkingOfYouPreferences.isPaired(applicationContext)) {
            glyphMatrixManager?.setMatrixFrame(IntArray(625))
            return
        }

        if (animationFrames.isNotEmpty()) {
            glyphMatrixManager?.setMatrixFrame(animationFrames[0])
        }
    }

    private fun playReceiveSound() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(applicationContext, R.raw.thinking_of_you_receive)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing receive sound", e)
        }
    }

    private fun diamond489ToGrid625(pixels: IntArray): IntArray {
        val diamondRowLengths = intArrayOf(
            7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7
        )

        val out = IntArray(625)
        var idx = 0
        for (row in 0 until 25) {
            val len = diamondRowLengths[row]
            val startCol = (25 - len) / 2
            for (c in 0 until len) {
                if (idx < pixels.size) {
                    out[row * 25 + startCol + c] = pixels[idx++]
                }
            }
        }
        return out
    }
}
