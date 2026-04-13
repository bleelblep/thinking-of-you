package com.bleelblep.thinkingofyou.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import org.json.JSONObject
import kotlin.random.Random

/**
 * Plays animations on the Glyph Matrix using setAppMatrixFrame.
 * Uses setAppMatrixFrame for app-level matrix control (requires system version 20250801+).
 */
class GlyphAnimationPlayer(private val context: Context) {

    data class AnimationFrame(
        val pixels: IntArray,
        val durationMs: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AnimationFrame
            if (!pixels.contentEquals(other.pixels)) return false
            if (durationMs != other.durationMs) return false
            return true
        }

        override fun hashCode(): Int {
            var result = pixels.contentHashCode()
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    data class Animation(
        val frames: List<AnimationFrame>,
        val totalDurationMs: Long
    )

    private val handler = Handler(Looper.getMainLooper())
    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var isServiceConnected = false
    private var isPlaying = false
    private var isLooping = false
    private var currentFrameIndex = 0
    private var currentAnimation: Animation? = null
    private var onCompleteCallback: (() -> Unit)? = null

    private var pendingAnimation: Pair<Animation, (() -> Unit)?>? = null

    private val animationCache = mutableMapOf<String, Animation>()

    private val gmmCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            Log.d(TAG, "GlyphMatrixManager service connected")
            glyphMatrixManager?.register(Glyph.DEVICE_23112)
            isServiceConnected = true

            pendingAnimation?.let { (animation, callback) ->
                pendingAnimation = null
                playAnimation(animation, callback)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            Log.d(TAG, "GlyphMatrixManager service disconnected")
            isServiceConnected = false
        }
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying) return

            val animation = currentAnimation
            if (animation == null || animation.frames.isEmpty()) {
                stopAnimation()
                return
            }

            if (currentFrameIndex >= animation.frames.size) {
                if (isLooping) {
                    currentFrameIndex = 0
                } else {
                    stopAnimation()
                    onCompleteCallback?.invoke()
                    return
                }
            }

            val frame = animation.frames.getOrNull(currentFrameIndex)
            if (frame == null) {
                Log.w(TAG, "Invalid frame index: $currentFrameIndex, stopping animation")
                stopAnimation()
                return
            }

            displayFrame(frame.pixels)
            currentFrameIndex++

            if (currentFrameIndex < animation.frames.size) {
                handler.postDelayed(this, frame.durationMs)
            } else if (isLooping) {
                handler.postDelayed(this, frame.durationMs)
            } else {
                handler.postDelayed({
                    stopAnimation()
                    onCompleteCallback?.invoke()
                }, frame.durationMs)
            }
        }
    }

    fun initialize(): Boolean {
        return try {
            glyphMatrixManager = GlyphMatrixManager.getInstance(context)
            if (glyphMatrixManager != null) {
                glyphMatrixManager?.init(gmmCallback)
                Log.d(TAG, "GlyphMatrixManager initialized, waiting for service connection")
                true
            } else {
                Log.w(TAG, "GlyphMatrixManager is null - device may not support Glyph")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GlyphMatrixManager", e)
            false
        }
    }

    fun uninitialize() {
        try {
            stopAnimation()
            glyphMatrixManager?.unInit()
            glyphMatrixManager = null
            isServiceConnected = false
            Log.d(TAG, "GlyphMatrixManager uninitialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error uninitializing GlyphMatrixManager", e)
        }
    }

    fun loadAnimation(assetPath: String): Animation? {
        animationCache[assetPath]?.let { return it }

        return try {
            val content = context.assets.open(assetPath).bufferedReader().use { it.readText() }

            val frames = if (assetPath.endsWith(".json")) {
                parseJsonAnimation(content)
            } else {
                parseTxtAnimation(content)
            }

            if (frames.isEmpty()) {
                Log.e(TAG, "No frames in $assetPath")
                return null
            }

            val totalDuration = frames.sumOf { it.durationMs }
            val animation = Animation(frames, totalDuration)
            animationCache[assetPath] = animation
            Log.d(TAG, "Loaded animation: $assetPath (${frames.size} frames, ${totalDuration}ms)")
            animation
        } catch (e: Exception) {
            Log.e(TAG, "Error loading animation $assetPath", e)
            null
        }
    }

    private fun parseJsonAnimation(content: String): List<AnimationFrame> {
        val json = JSONObject(content)
        val framesArray = json.getJSONArray("frames")
        val frames = mutableListOf<AnimationFrame>()

        for (i in 0 until framesArray.length()) {
            val frameJson = framesArray.getJSONObject(i)
            val durationMs = frameJson.optLong("d", DEFAULT_FRAME_DURATION_MS)
            val pixelsArray = frameJson.getJSONArray("p")

            val rawPixels = IntArray(pixelsArray.length())
            for (j in 0 until pixelsArray.length()) {
                rawPixels[j] = pixelsArray.getInt(j)
            }

            frames.add(createFrame(rawPixels, durationMs))
        }

        return frames
    }

    private fun parseTxtAnimation(content: String): List<AnimationFrame> {
        val frames = mutableListOf<AnimationFrame>()
        val lines = content.trim().split("\n").filter { it.isNotBlank() }

        for (line in lines) {
            val pixelStrings = line.trim().split(",")
            val rawPixels = IntArray(pixelStrings.size) { idx ->
                pixelStrings[idx].trim().toIntOrNull() ?: 0
            }
            frames.add(createFrame(rawPixels, DEFAULT_FRAME_DURATION_MS))
        }

        return frames
    }

    private fun createFrame(rawPixels: IntArray, durationMs: Long): AnimationFrame {
        val gridPixels = when (rawPixels.size) {
            DIAMOND_PIXELS -> diamond489ToGrid625(rawPixels)
            GRID_PIXELS -> rawPixels
            else -> {
                Log.w(TAG, "Unexpected pixel count: ${rawPixels.size}, padding/truncating")
                IntArray(GRID_PIXELS) { idx -> rawPixels.getOrNull(idx) ?: 0 }
            }
        }

        val scaledPixels = IntArray(GRID_PIXELS)
        for (j in 0 until GRID_PIXELS) {
            scaledPixels[j] = (gridPixels[j] * MAX_BRIGHTNESS) / 255
        }

        return AnimationFrame(scaledPixels, durationMs)
    }

    fun loadAnimatedHeart(): Animation? {
        return loadAnimation("thinkingofyou/sparkling_heart_anim_2.json")
    }

    fun loadStaticHeart(): IntArray? {
        val animation = loadAnimation("thinkingofyou/sparkling_heart_static_2.json")
        return animation?.frames?.firstOrNull()?.pixels
    }

    fun generateConfettiAnimation(): Animation {
        val frames = mutableListOf<AnimationFrame>()
        val numFrames = 60
        val frameDuration = 50L

        data class Particle(
            var x: Float,
            var y: Float,
            var vx: Float,
            var vy: Float,
            var brightness: Int,
            var life: Int
        )

        val particles = mutableListOf<Particle>()
        repeat(30) {
            particles.add(Particle(
                x = 12f + Random.nextFloat() * 2 - 1,
                y = 12f + Random.nextFloat() * 2 - 1,
                vx = Random.nextFloat() * 2f - 1f,
                vy = Random.nextFloat() * -2f - 0.5f,
                brightness = (180 + Random.nextInt(75)),
                life = 30 + Random.nextInt(30)
            ))
        }

        for (frame in 0 until numFrames) {
            val pixels = IntArray(GRID_PIXELS)

            if (frame % 5 == 0 && frame < 40) {
                repeat(3) {
                    particles.add(Particle(
                        x = Random.nextFloat() * MATRIX_SIZE,
                        y = -2f,
                        vx = Random.nextFloat() * 0.6f - 0.3f,
                        vy = Random.nextFloat() * 1.5f + 0.5f,
                        brightness = (150 + Random.nextInt(105)),
                        life = 20 + Random.nextInt(25)
                    ))
                }
            }

            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.08f
                p.life--

                val fadeFactor = (p.life / 30f).coerceIn(0f, 1f)
                val currentBrightness = ((p.brightness * fadeFactor) * MAX_BRIGHTNESS / 255).toInt()

                if (p.life <= 0 || p.y > MATRIX_SIZE + 2 || p.x < -2 || p.x > MATRIX_SIZE + 2) {
                    iterator.remove()
                } else {
                    val px = p.x.toInt()
                    val py = p.y.toInt()
                    if (px in 0 until MATRIX_SIZE && py in 0 until MATRIX_SIZE) {
                        val idx = py * MATRIX_SIZE + px
                        pixels[idx] = maxOf(pixels[idx], currentBrightness)
                    }
                }
            }

            frames.add(AnimationFrame(pixels, frameDuration))
        }

        return Animation(frames, frames.sumOf { it.durationMs })
    }

    private val font5x5: Map<Char, List<Int>> = mapOf(
        'A' to listOf(0b01110, 0b10001, 0b11111, 0b10001, 0b10001),
        'B' to listOf(0b11110, 0b10001, 0b11110, 0b10001, 0b11110),
        'C' to listOf(0b01111, 0b10000, 0b10000, 0b10000, 0b01111),
        'D' to listOf(0b11110, 0b10001, 0b10001, 0b10001, 0b11110),
        'E' to listOf(0b11111, 0b10000, 0b11110, 0b10000, 0b11111),
        'F' to listOf(0b11111, 0b10000, 0b11110, 0b10000, 0b10000),
        'G' to listOf(0b01111, 0b10000, 0b10011, 0b10001, 0b01111),
        'H' to listOf(0b10001, 0b10001, 0b11111, 0b10001, 0b10001),
        'I' to listOf(0b11111, 0b00100, 0b00100, 0b00100, 0b11111),
        'J' to listOf(0b00111, 0b00001, 0b00001, 0b10001, 0b01110),
        'K' to listOf(0b10001, 0b10010, 0b11100, 0b10010, 0b10001),
        'L' to listOf(0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
        'M' to listOf(0b10001, 0b11011, 0b10101, 0b10001, 0b10001),
        'N' to listOf(0b10001, 0b11001, 0b10101, 0b10011, 0b10001),
        'O' to listOf(0b01110, 0b10001, 0b10001, 0b10001, 0b01110),
        'P' to listOf(0b11110, 0b10001, 0b11110, 0b10000, 0b10000),
        'Q' to listOf(0b01110, 0b10001, 0b10101, 0b10010, 0b01101),
        'R' to listOf(0b11110, 0b10001, 0b11110, 0b10010, 0b10001),
        'S' to listOf(0b01111, 0b10000, 0b01110, 0b00001, 0b11110),
        'T' to listOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100),
        'U' to listOf(0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'V' to listOf(0b10001, 0b10001, 0b10001, 0b01010, 0b00100),
        'W' to listOf(0b10001, 0b10001, 0b10101, 0b11011, 0b10001),
        'X' to listOf(0b10001, 0b01010, 0b00100, 0b01010, 0b10001),
        'Y' to listOf(0b10001, 0b01010, 0b00100, 0b00100, 0b00100),
        'Z' to listOf(0b11111, 0b00010, 0b00100, 0b01000, 0b11111),
        '0' to listOf(0b01110, 0b10011, 0b10101, 0b11001, 0b01110),
        '1' to listOf(0b00100, 0b01100, 0b00100, 0b00100, 0b01110),
        '2' to listOf(0b01110, 0b10001, 0b00110, 0b01000, 0b11111),
        '3' to listOf(0b11110, 0b00001, 0b00110, 0b00001, 0b11110),
        '4' to listOf(0b10001, 0b10001, 0b11111, 0b00001, 0b00001),
        '5' to listOf(0b11111, 0b10000, 0b11110, 0b00001, 0b11110),
        '6' to listOf(0b01110, 0b10000, 0b11110, 0b10001, 0b01110),
        '7' to listOf(0b11111, 0b00001, 0b00010, 0b00100, 0b00100),
        '8' to listOf(0b01110, 0b10001, 0b01110, 0b10001, 0b01110),
        '9' to listOf(0b01110, 0b10001, 0b01111, 0b00001, 0b01110),
        ' ' to listOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000),
        '!' to listOf(0b00100, 0b00100, 0b00100, 0b00000, 0b00100),
        '?' to listOf(0b01110, 0b10001, 0b00110, 0b00000, 0b00100),
        '<' to listOf(0b00010, 0b00100, 0b01000, 0b00100, 0b00010),
        '>' to listOf(0b01000, 0b00100, 0b00010, 0b00100, 0b01000),
        '+' to listOf(0b00000, 0b00100, 0b01110, 0b00100, 0b00000),
        '-' to listOf(0b00000, 0b00000, 0b01110, 0b00000, 0b00000),
        '*' to listOf(0b00000, 0b01010, 0b00100, 0b01010, 0b00000),
        ':' to listOf(0b00000, 0b00100, 0b00000, 0b00100, 0b00000)
    )

    companion object {
        private const val TAG = "GlyphAnimationPlayer"
        private const val MATRIX_SIZE = 25
        private const val GRID_PIXELS = 625
        private const val DIAMOND_PIXELS = 489
        private const val MAX_BRIGHTNESS = 2047
        private const val DEFAULT_FRAME_DURATION_MS = 50L
        private const val MAX_TEXT_LENGTH = 50

        private val DIAMOND_ROW_LENGTHS = intArrayOf(
            7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7
        )
    }

    fun generateTextScrollAnimation(text: String): Animation {
        val frames = mutableListOf<AnimationFrame>()
        val frameDuration = 80L
        val charWidth = 6
        val displayText = text.take(MAX_TEXT_LENGTH).uppercase()

        val totalWidth = displayText.length * charWidth + MATRIX_SIZE

        val yOffset = (MATRIX_SIZE - 5) / 2

        for (scrollPos in 0 until totalWidth) {
            val pixels = IntArray(GRID_PIXELS)

            for ((charIndex, char) in displayText.withIndex()) {
                val charX = MATRIX_SIZE - scrollPos + charIndex * charWidth
                val pattern = font5x5[char] ?: font5x5[' ']!!

                for (row in 0 until 5) {
                    val rowPattern = pattern[row]
                    for (col in 0 until 5) {
                        if ((rowPattern shr (4 - col)) and 1 == 1) {
                            val px = charX + col
                            val py = yOffset + row
                            if (px in 0 until MATRIX_SIZE && py in 0 until MATRIX_SIZE) {
                                pixels[py * MATRIX_SIZE + px] = MAX_BRIGHTNESS
                            }
                        }
                    }
                }
            }

            frames.add(AnimationFrame(pixels, frameDuration))
        }

        return Animation(frames, frames.sumOf { it.durationMs })
    }

    fun playConfetti(loop: Boolean = false, onComplete: (() -> Unit)? = null) {
        val animation = generateConfettiAnimation()
        isLooping = loop
        playAnimation(animation, onComplete)
    }

    fun playTextScroll(text: String, loop: Boolean = false, onComplete: (() -> Unit)? = null) {
        val animation = generateTextScrollAnimation(text)
        isLooping = loop
        playAnimation(animation, onComplete)
    }

    fun playAnimation(animation: Animation, onComplete: (() -> Unit)? = null) {
        if (animation.frames.isEmpty()) {
            Log.w(TAG, "Cannot play empty animation")
            onComplete?.invoke()
            return
        }

        if (!isServiceConnected) {
            Log.d(TAG, "Service not connected yet, queueing animation")
            pendingAnimation = Pair(animation, onComplete)
            return
        }

        stopAnimation()

        currentAnimation = animation
        currentFrameIndex = 0
        onCompleteCallback = onComplete
        isPlaying = true

        Log.d(TAG, "Starting animation playback (${animation.frames.size} frames)")
        handler.post(frameRunnable)
    }

    fun playAnimatedHeart(loop: Boolean = false, onComplete: (() -> Unit)? = null) {
        val animation = loadAnimatedHeart()
        if (animation != null) {
            isLooping = loop
            playAnimation(animation, onComplete)
        } else {
            Log.e(TAG, "Failed to load animated heart")
            onComplete?.invoke()
        }
    }

    fun stopLooping() {
        isLooping = false
    }

    fun displayStaticFrame(pixels: IntArray) {
        stopAnimation()
        displayFrame(pixels)
    }

    fun displayStaticHeart() {
        val pixels = loadStaticHeart()
        if (pixels != null) {
            displayStaticFrame(pixels)
        } else {
            Log.e(TAG, "Failed to load static heart")
        }
    }

    fun stopAnimation() {
        isPlaying = false
        handler.removeCallbacks(frameRunnable)
        currentAnimation = null
        currentFrameIndex = 0
        onCompleteCallback = null

        closeAppMatrix()
    }

    private fun displayFrame(pixels: IntArray) {
        try {
            val gmm = glyphMatrixManager ?: GlyphMatrixManager.getInstance(context)
            if (gmm == null) {
                Log.w(TAG, "GlyphMatrixManager not available")
                return
            }
            glyphMatrixManager = gmm

            gmm.setAppMatrixFrame(pixels)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying frame via setAppMatrixFrame", e)
        }
    }

    fun closeAppMatrix() {
        try {
            glyphMatrixManager?.closeAppMatrix()
            Log.d(TAG, "App matrix closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing app matrix", e)
        }
    }

    fun turnOff() {
        stopAnimation()
        try {
            glyphMatrixManager?.turnOff()
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off matrix", e)
        }
    }

    fun isSupported(): Boolean {
        return try {
            GlyphMatrixManager.getInstance(context) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun diamond489ToGrid625(pixels: IntArray): IntArray {
        val out = IntArray(GRID_PIXELS)
        var idx = 0
        for (row in 0 until MATRIX_SIZE) {
            val len = DIAMOND_ROW_LENGTHS[row]
            val startCol = (MATRIX_SIZE - len) / 2
            for (c in 0 until len) {
                if (idx < pixels.size) {
                    out[row * MATRIX_SIZE + startCol + c] = pixels[idx++]
                }
            }
        }
        return out
    }

    fun clearCache() {
        animationCache.clear()
        Log.d(TAG, "Animation cache cleared")
    }
}
