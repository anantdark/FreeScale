package com.anant.freescale.ui.loading.animations

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anant.freescale.ui.loading.LoadingAnimation
import com.anant.freescale.ui.loading.LoadingAnimationScope
import com.anant.freescale.ui.loading.LoadingAnimationSlot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Full-card tiranga: flowing saffron / white / green silk of the Indian flag,
 * with a soft 3D breathing glow. The Ashoka Chakra stays a fixed size in the
 * white band and spins steadily. Press-and-hold speeds fabric + spin.
 */
object TirangaLoadingAnimation : LoadingAnimation {
    override val id: String = "tiranga"
    override val displayName: String = "Indian flag"
    override val slots: Set<LoadingAnimationSlot> = setOf(LoadingAnimationSlot.READING)
    override val defaultCaptions: List<String> = measuringCaptions
    override val lightContent: Boolean = false

    @Composable
    override fun Content(scope: LoadingAnimationScope) {
        TirangaCard(
            modelId = scope.label,
            captions = scope.captions.ifEmpty { defaultCaptions },
            speedMultiplier = scope.speedMultiplier,
            modifier = scope.modifier,
        )
    }
}

@Composable
private fun TirangaCard(
    modelId: String?,
    captions: List<String>,
    speedMultiplier: Float,
    modifier: Modifier = Modifier,
) {
    var sceneTime by remember { mutableStateOf(0.0) }
    var lastFrame by remember { mutableLongStateOf(0L) }
    val speedState = remember { mutableStateOf(speedMultiplier) }
    speedState.value = speedMultiplier

    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                val delta = if (lastFrame == 0L) 0L else (now - lastFrame)
                lastFrame = now
                sceneTime = accumulateScaledTime(sceneTime, delta, speedState.value)
            }
        }
    }

    var captionIndex by remember { mutableStateOf(0) }
    LaunchedEffect(captions) {
        if (captions.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(8_000L)
            captionIndex = (captionIndex + 1) % captions.size
        }
    }

    val firstLine = measuringLabel(modelId)
    val boosting = speedMultiplier > 1.05f

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height
            val short = min(W, H)
            val t = sceneTime

            // Organic flowing silk — seamless band blends, no traveling stripe.
            drawTirangaCardFabric(
                timeMs = t,
                columns = 120,
                seamWobbleScale = if (boosting) 1.35f else 1.05f,
            )

            // Soft volumetric breath — diffuse only (no hard moving ridge).
            drawBreathingHueGlow(timeMs = t, boosting = boosting)

            // Fixed-size Ashoka Chakra, vertically centered in the white band.
            val chakraCenter = Offset(W * 0.78f, H * 0.50f)
            val chakraR = short * 0.17f

            // Soft white disc so navy spokes stay crisp on the flowing white band.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TirangaFlagWhite.copy(alpha = 0.55f),
                        TirangaFlagWhite.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = chakraCenter,
                    radius = chakraR * 1.45f,
                ),
                radius = chakraR * 1.45f,
                center = chakraCenter,
            )

            // Gentle glow pulse around the wheel (halo only — radius of the chakra itself is fixed).
            val halo = 0.55f + 0.45f * (0.5f + 0.5f * sin(t * 0.0022).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        TirangaChakraNavy.copy(alpha = 0.10f * halo * if (boosting) 1.2f else 1f),
                        Color.Transparent,
                    ),
                    center = chakraCenter,
                    radius = chakraR * 1.85f,
                ),
                radius = chakraR * 1.85f,
                center = chakraCenter,
            )

            drawSpinningAshokaChakra(
                center = chakraCenter,
                outerRadius = chakraR,
                timeMs = t * 0.30,
                color = TirangaChakraNavy,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Captions sit on green — white for legibility without changing flag colours.
            Text(
                text = firstLine,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = TirangaFlagWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (captions.isNotEmpty()) {
                Text(
                    text = captions[captionIndex % captions.size],
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    ),
                    color = TirangaFlagWhite.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Soft volumetric lighting that breathes over the flag. Uses drifting radial
 * pools instead of a single scanning stripe so no hard line is visible.
 */
private fun DrawScope.drawBreathingHueGlow(timeMs: Double, boosting: Boolean) {
    val W = size.width
    val H = size.height
    val t = timeMs
    val amp = if (boosting) 1.2f else 1f

    // Slow incommensurate breath envelopes.
    val b1 = 0.5f + 0.5f * sin(t * 0.00105).toFloat()
    val b2 = 0.5f + 0.5f * sin(t * 0.00073 + 1.7).toFloat()
    val b3 = 0.5f + 0.5f * cos(t * 0.00091 + 0.6).toFloat()

    // Diffuse catch-lights that wander with noise (no modulo wrap stripe).
    fun softPool(seed: Double, baseX: Float, baseY: Float, radius: Float, alpha: Float) {
        val dx = fabricWander(t, seed) * W * 0.22f
        val dy = fabricWander(t, seed + 2.3) * H * 0.10f
        val c = Offset(
            (baseX * W + dx).coerceIn(W * 0.08f, W * 0.92f),
            (baseY * H + dy).coerceIn(H * 0.08f, H * 0.92f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = alpha * amp),
                    Color.Transparent,
                ),
                center = c,
                radius = radius,
            ),
            radius = radius,
            center = c,
        )
    }

    softPool(0.4, 0.30f, 0.18f, H * 0.42f, 0.07f + 0.05f * b1) // saffron sky
    softPool(1.9, 0.62f, 0.50f, H * 0.38f, 0.06f + 0.04f * b2) // white mid
    softPool(3.7, 0.40f, 0.82f, H * 0.40f, 0.05f + 0.04f * b3) // green earth

    // Very soft side shade — cylindrical cloth feel, static enough to avoid lines.
    drawRect(
        brush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0f to Color.Black.copy(alpha = (0.07f + 0.03f * b1) * amp),
                0.22f to Color.Transparent,
                0.78f to Color.Transparent,
                1f to Color.Black.copy(alpha = (0.08f + 0.03f * b2) * amp),
            ),
        ),
    )
}

/** Smooth wander in ≈[-1, 1] from stacked slow sines (no hard loop). */
private fun fabricWander(tMs: Double, seed: Double): Float {
    val a = sin(tMs * 0.00031 + seed).toFloat()
    val b = sin(tMs * 0.00047 + seed * 1.7).toFloat()
    val c = cos(tMs * 0.00023 - seed * 0.9).toFloat()
    return a * 0.50f + b * 0.32f + c * 0.18f
}
