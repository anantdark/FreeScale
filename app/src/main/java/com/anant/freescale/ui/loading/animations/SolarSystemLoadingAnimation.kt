package com.anant.freescale.ui.loading.animations

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anant.freescale.ui.loading.LoadingAnimation
import com.anant.freescale.ui.loading.LoadingAnimationScope
import com.anant.freescale.ui.loading.LoadingAnimationSlot
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Full-card observatory: sun locked to the top-right progress corner,
 * planets on near-circular top-down Keplerian orbits sweeping the readout.
 */
object SolarSystemLoadingAnimation : LoadingAnimation {
    override val id: String = "solar_system"
    override val displayName: String = "Solar system"
    override val slots: Set<LoadingAnimationSlot> = setOf(LoadingAnimationSlot.READING)
    override val defaultCaptions: List<String> = measuringCaptions
    override val lightContent: Boolean = true

    @Composable
    override fun Content(scope: LoadingAnimationScope) {
        SolarSystemCard(
            modelId = scope.label,
            captions = scope.captions.ifEmpty { defaultCaptions },
            speedMultiplier = scope.speedMultiplier,
            modifier = scope.modifier,
        )
    }
}

@Composable
private fun SolarSystemCard(
    modelId: String?,
    captions: List<String>,
    speedMultiplier: Float,
    modifier: Modifier = Modifier,
) {
    var scaledTime by remember { mutableStateOf(0.0) }
    var lastFrame by remember { mutableLongStateOf(0L) }
    val speedState = remember { mutableStateOf(speedMultiplier) }
    speedState.value = speedMultiplier

    LaunchedEffect(Unit) {
        while (true) {
            withInfiniteAnimationFrameMillis { now ->
                val delta = if (lastFrame == 0L) 0L else (now - lastFrame)
                lastFrame = now
                val speed = BANNER_SPEED_NATURAL * speedState.value
                scaledTime = accumulateScaledTime(scaledTime, delta, speed)
            }
        }
    }

    var captionIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(captions) {
        if (captions.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(8_000L)
            captionIndex = (captionIndex + 1) % captions.size
        }
    }

    val amber = Color(0xFFFFB300)
    val sunCore = Color(0xFFFFF8E1)
    val sunHot = Color(0xFFFFE082)
    val sunPlasma = Color(0xFFFF6D00)
    val sunLimb = Color(0xFFBF360C)
    val spaceDeep = Color(0xFF02030A)
    val spaceMid = Color(0xFF070B16)
    val planets = remember { solarSystemPlanets(BannerProteinColor, BannerCarbsColor, BannerFatsColor) }
    val firstLine = measuringLabel(modelId)
    val isBoosting = speedMultiplier > 1.05f

    data class Star(
        val x: Float,
        val y: Float,
        val baseAlpha: Float,
        val freq: Double,
        val phase: Double,
        val radius: Float,
        val layer: Float,
        val tint: Color,
    )
    val stars = remember {
        val rng = kotlin.random.Random(0xA57A_2024)
        val tints = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFE3ECFF),
            Color(0xFFFFF1DD),
            Color(0xFFC9D7FF),
            Color(0xFFFFE0C2),
        )
        List(160) {
            Star(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                baseAlpha = 0.08f + rng.nextFloat() * 0.65f,
                freq = 0.0004 + rng.nextDouble() * 0.0028,
                phase = rng.nextDouble() * (2.0 * PI),
                radius = 0.35f + rng.nextFloat() * 1.45f,
                layer = 0.15f + rng.nextFloat() * 0.85f,
                tint = tints[rng.nextInt(tints.size)],
            )
        }
    }

    data class ShootingStar(
        val startX: Float,
        val startY: Float,
        val dx: Float,
        val dy: Float,
        val periodMs: Double,
        val offsetMs: Double,
        val alpha: Float,
        val length: Float,
    )
    val shootingStars = remember {
        val rng = kotlin.random.Random(0xB33F_2025)
        List(5) {
            val angle = Math.PI + (-0.35 + rng.nextDouble() * 0.70)
            ShootingStar(
                startX = 0.15f + rng.nextFloat() * 0.75f,
                startY = rng.nextFloat() * 0.45f,
                dx = cos(angle).toFloat(),
                dy = sin(angle).toFloat() * 0.55f,
                periodMs = 3800.0 + rng.nextDouble() * 5200.0,
                offsetMs = rng.nextDouble() * 8000.0,
                alpha = 0.40f + rng.nextFloat() * 0.40f,
                length = 0.08f + rng.nextFloat() * 0.10f,
            )
        }
    }

    val arcRadians = 0.48
    val arcSteps = 10

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height
            val scale = min(W, H)
            val now = scaledTime
            val pulse = 0.94f + 0.06f * sin(now * 0.0022).toFloat()
            val boost = isBoosting

            // Sun sits under the top-right circular progress (card padding ~24dp).
            val sunPos = Offset(W - 42.dp.toPx(), 42.dp.toPx())
            val sunDiskR = 14.dp.toPx() * pulse

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(spaceMid, spaceDeep, Color(0xFF010208)),
                    center = Offset(W * 0.72f, H * 0.18f),
                    radius = hypot(W, H),
                ),
            )

            if (boost) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            amber.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        center = sunPos,
                        radius = scale * 0.9f,
                    ),
                )
            }

            stars.forEach { s ->
                val twinkle = sin(now * s.freq + s.phase).toFloat()
                val a = (s.baseAlpha + twinkle * s.baseAlpha * 0.70f).coerceIn(0.02f, 1f)
                val spin = if (boost) (now * 0.00012 * s.layer).toFloat() else 0f
                val cx = ((s.x + spin) % 1f + 1f) % 1f
                drawCircle(
                    color = s.tint.copy(alpha = a * if (boost) 0.85f else 1f),
                    radius = s.radius * if (boost) 1.15f else 1f,
                    center = Offset(cx * W, s.y * H),
                )
            }

            if (!boost) {
                shootingStars.forEach { ss ->
                    val t = (((now + ss.offsetMs) % ss.periodMs) / ss.periodMs).toFloat()
                    if (t < 0.12f) {
                        val u = t / 0.12f
                        val travel = ss.length * u
                        val head = Offset(
                            (ss.startX + ss.dx * travel) * W,
                            (ss.startY + ss.dy * travel) * H,
                        )
                        val tailU = (u - 0.5f).coerceAtLeast(0f)
                        val tail = Offset(
                            (ss.startX + ss.dx * ss.length * tailU) * W,
                            (ss.startY + ss.dy * ss.length * tailU) * H,
                        )
                        val a = ss.alpha * (1f - abs(u - 0.45f) * 2.2f).coerceIn(0f, 1f)
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = a)),
                                start = tail,
                                end = head,
                            ),
                            start = tail,
                            end = head,
                            strokeWidth = 1.2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            // Soft solar bloom across the card — reads as atmosphere, not a bar.
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to amber.copy(alpha = 0.16f * pulse),
                        0.35f to amber.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                    ),
                    center = sunPos,
                    radius = scale * 0.72f,
                ),
                radius = scale * 0.72f,
                center = sunPos,
                blendMode = BlendMode.Plus,
            )

            planets.forEach { spec ->
                drawTopDownOrbit(sunPos, spec, scale, fade = if (boost) 0.45f else 1f)
            }

            val states = planets.map { spec ->
                spec to orbitalPoint(spec, now, sunPos.x, sunPos.y, scale)
            }
            val (backPlanets, frontPlanets) = orderByDepth(states)

            fun drawPlanet(spec: PlanetSpec, pos: Triple<Float, Float, Float>) {
                val (hx, hy, hz) = pos
                val distToSun = hypot(hx - sunPos.x, hy - sunPos.y)
                val occulted = hz < 0f && distToSun < sunDiskR * 0.95f
                val transiting = hz >= 0f && distToSun < sunDiskR * 1.12f

                if (!occulted && !boost) {
                    val stepMs = (arcRadians / spec.angularVel.coerceAtLeast(1e-9)) / arcSteps
                    drawOrbitArc(
                        spec = spec,
                        now = now,
                        sunPos = sunPos,
                        scale = scale,
                        steps = arcSteps,
                        stepMs = stepMs,
                        depthZ = hz,
                        fade = 1f,
                    )
                }
                if (occulted) return

                drawLitPlanet(
                    center = Offset(hx, hy),
                    sunPos = sunPos,
                    color = spec.color,
                    scale = depthScale(hz) * spec.bodyScale,
                    alpha = depthAlpha(hz) * if (transiting) 0.80f else 1f,
                    transit = transiting,
                    hasRing = spec.hasRing,
                )
            }

            backPlanets.forEach { (spec, pos) -> drawPlanet(spec, pos) }
            drawSun(sunPos, amber, sunHot, sunCore, sunPlasma, sunLimb, pulse, now, sunDiskR)
            frontPlanets.forEach { (spec, pos) -> drawPlanet(spec, pos) }

            // Readability veil: keep weight / ticks legible over orbit traffic.
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.45f to Color.Black.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    ),
                    center = Offset(W * 0.32f, H * 0.48f),
                    radius = scale * 0.78f,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.22f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = firstLine,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (captions.isNotEmpty()) {
                Crossfade(
                    targetState = captionIndex % captions.size,
                    animationSpec = tween(durationMillis = 450),
                    label = "solar-caption",
                ) { index ->
                    Text(
                        text = captions[index],
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                        ),
                        color = Color.White.copy(alpha = 0.48f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawTopDownOrbit(
    sunPos: Offset,
    spec: PlanetSpec,
    scale: Float,
    fade: Float,
) {
    val a = spec.orbitA * scale
    val b = spec.orbitB * scale
    val path = Path()
    val samples = 80
    for (i in 0..samples) {
        val th = (i.toDouble() / samples) * 2.0 * PI
        val cosT = cos(th).toFloat()
        val sinT = sin(th).toFloat()
        val foreshorten = 0.94f + 0.06f * ((sinT + 1f) / 2f)
        val x = sunPos.x + a * cosT * foreshorten
        val y = sunPos.y + b * sinT
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(
        path = path,
        color = spec.color.copy(alpha = 0.07f * fade),
        style = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawOrbitArc(
    spec: PlanetSpec,
    now: Double,
    sunPos: Offset,
    scale: Float,
    steps: Int,
    stepMs: Double,
    depthZ: Float,
    fade: Float,
) {
    val pts = ArrayList<Offset>(steps + 1)
    for (step in steps downTo 1) {
        val past = now - step * stepMs
        val (qx, qy, _) = orbitalPoint(spec, past, sunPos.x, sunPos.y, scale)
        pts.add(Offset(qx, qy))
    }
    val (hx, hy, _) = orbitalPoint(spec, now, sunPos.x, sunPos.y, scale)
    pts.add(Offset(hx, hy))

    val baseW = 2.2.dp.toPx() * depthScale(depthZ) * spec.bodyScale.coerceAtMost(1.3f)
    for (i in 0 until pts.size - 1) {
        val frac = (i + 1).toFloat() / pts.size
        val a = frac * 0.42f * depthAlpha(depthZ) * fade
        drawLine(
            color = spec.color.copy(alpha = a),
            start = pts[i],
            end = pts[i + 1],
            strokeWidth = (baseW * frac).coerceAtLeast(0.7.dp.toPx()),
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawSun(
    sunPos: Offset,
    amber: Color,
    sunHot: Color,
    sunCore: Color,
    sunPlasma: Color,
    sunLimb: Color,
    pulse: Float,
    now: Double,
    diskR: Float,
) {
    val f = pulse

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(amber.copy(alpha = 0.22f * f), Color.Transparent),
            center = sunPos,
            radius = diskR * 4.8f,
        ),
        radius = diskR * 4.8f,
        center = sunPos,
        blendMode = BlendMode.Plus,
    )

    val rayCount = 16
    for (i in 0 until rayCount) {
        val baseAng = (i * (2.0 * PI) / rayCount) + now * 0.00032
        val flutter = 0.50f + 0.50f * sin(now * 0.0025 + i * 1.4).toFloat()
        val len = diskR * (1.6f + 2.4f * flutter)
        val dx = cos(baseAng).toFloat()
        val dy = sin(baseAng).toFloat()
        val tip = Offset(sunPos.x + dx * len, sunPos.y + dy * len)
        val root = Offset(sunPos.x + dx * diskR * 0.88f, sunPos.y + dy * diskR * 0.88f)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    sunPlasma.copy(alpha = 0.42f * flutter * f),
                    amber.copy(alpha = 0.14f * f),
                    Color.Transparent,
                ),
                start = root,
                end = tip,
            ),
            start = root,
            end = tip,
            strokeWidth = (1.8.dp.toPx() * flutter).coerceAtLeast(0.7.dp.toPx()),
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus,
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                sunHot.copy(alpha = 0.55f * f),
                sunPlasma.copy(alpha = 0.24f * f),
                Color.Transparent,
            ),
            center = sunPos,
            radius = diskR * 1.85f,
        ),
        radius = diskR * 1.85f,
        center = sunPos,
        blendMode = BlendMode.Plus,
    )

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to sunCore.copy(alpha = f),
                0.35f to sunHot.copy(alpha = f),
                0.72f to amber.copy(alpha = f),
                1.0f to sunLimb.copy(alpha = 0.92f * f),
            ),
            center = Offset(sunPos.x - diskR * 0.08f, sunPos.y - diskR * 0.10f),
            radius = diskR * 1.08f,
        ),
        radius = diskR,
        center = sunPos,
    )

    for (i in 0 until 8) {
        val ang = i * 0.95 + now * 0.0007
        val r = diskR * (0.18f + 0.22f * ((i * 37) % 5) / 5f)
        val c = Offset(
            sunPos.x + cos(ang).toFloat() * r * 0.55f,
            sunPos.y + sin(ang).toFloat() * r * 0.50f,
        )
        drawCircle(
            color = sunCore.copy(alpha = 0.12f * f),
            radius = diskR * (0.12f + 0.04f * (i % 3)),
            center = c,
            blendMode = BlendMode.Plus,
        )
    }

    drawCircle(
        color = Color.White.copy(alpha = 0.70f * f),
        radius = diskR * 0.24f,
        center = Offset(sunPos.x - diskR * 0.12f, sunPos.y - diskR * 0.10f),
        blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.drawLitPlanet(
    center: Offset,
    sunPos: Offset,
    color: Color,
    scale: Float,
    alpha: Float,
    transit: Boolean,
    hasRing: Boolean,
) {
    val bodyR = 4.2.dp.toPx() * scale
    val dx = sunPos.x - center.x
    val dy = sunPos.y - center.y
    val dist = hypot(dx, dy).coerceAtLeast(0.001f)
    val nx = dx / dist
    val ny = dy / dist

    if (hasRing) {
        val ringAngle = Math.toDegrees(atan2(ny.toDouble(), nx.toDouble())).toFloat()
        rotate(degrees = ringAngle * 0.15f, pivot = center) {
            drawOval(
                color = color.copy(alpha = 0.30f * alpha),
                topLeft = Offset(center.x - bodyR * 2.2f, center.y - bodyR * 0.45f),
                size = Size(bodyR * 4.4f, bodyR * 0.9f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.30f * alpha), Color.Transparent),
            center = center,
            radius = bodyR * 2.4f,
        ),
        radius = bodyR * 2.4f,
        center = center,
        blendMode = BlendMode.Plus,
    )

    if (transit) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f * alpha),
            radius = bodyR,
            center = center,
        )
    }

    drawCircle(
        color = color.copy(alpha = 0.18f * alpha),
        radius = bodyR,
        center = center,
    )

    val litCenter = Offset(center.x + nx * bodyR * 0.42f, center.y + ny * bodyR * 0.42f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.48f * alpha),
                color.copy(alpha = 0.96f * alpha),
                color.copy(alpha = 0.08f * alpha),
            ),
            center = litCenter,
            radius = bodyR * 1.55f,
        ),
        radius = bodyR,
        center = center,
    )

    val darkCenter = Offset(center.x - nx * bodyR * 0.58f, center.y - ny * bodyR * 0.58f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.48f * alpha), Color.Transparent),
            center = darkCenter,
            radius = bodyR * 1.15f,
        ),
        radius = bodyR * 1.15f,
        center = darkCenter,
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.58f * alpha),
        radius = bodyR * 0.22f,
        center = Offset(center.x + nx * bodyR * 0.30f, center.y + ny * bodyR * 0.30f),
        blendMode = BlendMode.Plus,
    )
}
