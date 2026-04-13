package com.bleelblep.thinkingofyou.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bleelblep.thinkingofyou.service.PingService
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Full-screen overlay activity that shows a heart animation when a ping is received.
 * For devices without a Glyph matrix.
 */
class HeartOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOOP = "loop_animation"
    }

    private val stopAnimationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PingService.STOP_ANIMATION_ACTION) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        registerReceiver(
            stopAnimationReceiver,
            IntentFilter(PingService.STOP_ANIMATION_ACTION),
            Context.RECEIVER_NOT_EXPORTED
        )

        val shouldLoop = intent.getBooleanExtra(EXTRA_LOOP, false)

        setContent {
            HeartOverlayScreen(
                loop = shouldLoop,
                onDismiss = {
                    PingService.stopAnimation(this@HeartOverlayActivity)
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopAnimationReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}

@Composable
fun HeartOverlayScreen(
    loop: Boolean,
    onDismiss: () -> Unit
) {
    LaunchedEffect(loop) {
        if (!loop) {
            delay(4000)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        SparkleParticles()
        AnimatedHeart(loop = loop)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Someone is thinking of you",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap anywhere to dismiss",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun AnimatedHeart(loop: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val entryScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "entry"
    )

    Canvas(
        modifier = Modifier.size(250.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val heartSize = size.minDimension * 0.4f * scale * entryScale

        drawHeart(
            centerX = centerX,
            centerY = centerY,
            size = heartSize * 1.3f,
            color = Color(0xFFFF6B6B).copy(alpha = glowAlpha * 0.3f)
        )

        drawHeart(
            centerX = centerX,
            centerY = centerY,
            size = heartSize * 1.15f,
            color = Color(0xFFFF6B6B).copy(alpha = glowAlpha * 0.5f)
        )

        drawHeart(
            centerX = centerX,
            centerY = centerY,
            size = heartSize,
            color = Color(0xFFFF6B6B)
        )

        drawHeart(
            centerX = centerX - heartSize * 0.05f,
            centerY = centerY - heartSize * 0.15f,
            size = heartSize * 0.5f,
            color = Color(0xFFFF9999).copy(alpha = 0.5f)
        )
    }
}

fun DrawScope.drawHeart(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        val width = size
        val height = size

        val bottomY = centerY + height * 0.4f
        val topY = centerY - height * 0.35f
        val midY = centerY - height * 0.1f

        moveTo(centerX, bottomY)

        cubicTo(
            centerX - width * 0.5f, centerY + height * 0.1f,
            centerX - width * 0.55f, topY,
            centerX - width * 0.28f, topY
        )

        cubicTo(
            centerX - width * 0.1f, topY,
            centerX, midY,
            centerX, midY
        )

        cubicTo(
            centerX, midY,
            centerX + width * 0.1f, topY,
            centerX + width * 0.28f, topY
        )

        cubicTo(
            centerX + width * 0.55f, topY,
            centerX + width * 0.5f, centerY + height * 0.1f,
            centerX, bottomY
        )

        close()
    }

    drawPath(path, color)
}

@Composable
fun SparkleParticles() {
    val particles = remember {
        List(20) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 8f + 4f,
                speed = Random.nextFloat() * 0.5f + 0.3f,
                delay = Random.nextInt(2000)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "sparkles")

    particles.forEach { particle ->
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000
                    0f at 0
                    1f at 500
                    1f at 1500
                    0f at 2000
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(particle.delay)
            ),
            label = "particle_alpha_${particle.hashCode()}"
        )

        val offsetY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -100f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(particle.delay)
            ),
            label = "particle_y_${particle.hashCode()}"
        )

        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particle_rot_${particle.hashCode()}"
        )

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val x = size.width * particle.x
            val y = size.height * particle.y + offsetY

            if (y > 0 && y < size.height) {
                rotate(rotation, pivot = Offset(x, y)) {
                    drawStar(
                        center = Offset(x, y),
                        size = particle.size,
                        color = Color.White.copy(alpha = alpha * 0.8f)
                    )
                }
            }
        }
    }
}

fun DrawScope.drawStar(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.3f, center.y - size * 0.3f)
        lineTo(center.x + size, center.y)
        lineTo(center.x + size * 0.3f, center.y + size * 0.3f)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.3f, center.y + size * 0.3f)
        lineTo(center.x - size, center.y)
        lineTo(center.x - size * 0.3f, center.y - size * 0.3f)
        close()
    }
    drawPath(path, color)
}

private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val delay: Int
)

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
