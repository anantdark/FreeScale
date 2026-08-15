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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

    val planets = remember { solarSystemPlanets() }
    val firstLine = measuringLabel(modelId)
    val isBoosting = speedMultiplier > 1.05f
    val sunTexture = rememberSunTexture()
    val sunSphereCache = rememberSunSphereCache()
    val sunSpots = rememberSunSpotSeeds()
    val planetTextures = rememberPlanetTextures()
    val planetSphereAtlas = rememberPlanetSphereAtlas()

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
            Color(0xFFE8EEFF),
            Color(0xFFFFF4E5),
            Color(0xFFB8C8FF),
            Color(0xFFFFE8C8),
            Color(0xFFFFD0A0),
        )
        List(220) {
            Star(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                baseAlpha = 0.04f + rng.nextFloat() * 0.72f,
                freq = 0.00025 + rng.nextDouble() * 0.0022,
                phase = rng.nextDouble() * (2.0 * PI),
                radius = 0.28f + rng.nextFloat() * 1.55f,
                layer = 0.12f + rng.nextFloat() * 0.88f,
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
        List(4) {
            val angle = Math.PI + (-0.35 + rng.nextDouble() * 0.70)
            ShootingStar(
                startX = 0.15f + rng.nextFloat() * 0.75f,
                startY = rng.nextFloat() * 0.45f,
                dx = cos(angle).toFloat(),
                dy = sin(angle).toFloat() * 0.55f,
                periodMs = 4200.0 + rng.nextDouble() * 5600.0,
                offsetMs = rng.nextDouble() * 8000.0,
                alpha = 0.35f + rng.nextFloat() * 0.35f,
                length = 0.07f + rng.nextFloat() * 0.09f,
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
            val pulse = 0.97f + 0.03f * sin(now * 0.0018).toFloat()
            val boost = isBoosting

            // Sun sits under the top-right circular progress (card padding ~24dp).
            val sunPos = Offset(W - 42.dp.toPx(), 42.dp.toPx())
            val sunDiskR = 20.dp.toPx() * pulse
            // Size orbits so Neptune (~orbitA 0.95) tracks near the sun→bottom-left diagonal.
            val orbitScale = hypot(sunPos.x, H - sunPos.y)

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A0E1A),
                        Color(0xFF04060E),
                        Color(0xFF010208),
                    ),
                    center = Offset(W * 0.70f, H * 0.16f),
                    radius = hypot(W, H),
                ),
            )

            // Faint zodiacal / galactic dust band.
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.42f to Color(0xFF1A1830).copy(alpha = 0.10f),
                        0.55f to Color(0xFF2A2040).copy(alpha = 0.07f),
                        1f to Color.Transparent,
                    ),
                    start = Offset(0f, H * 0.15f),
                    end = Offset(W, H * 0.95f),
                ),
            )

            if (boost) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB060).copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = sunPos,
                        radius = scale * 0.95f,
                    ),
                )
            }

            stars.forEach { s ->
                val twinkle = sin(now * s.freq + s.phase).toFloat()
                val a = (s.baseAlpha + twinkle * s.baseAlpha * 0.55f).coerceIn(0.02f, 1f)
                val spin = if (boost) (now * 0.00012 * s.layer).toFloat() else 0f
                val cx = ((s.x + spin) % 1f + 1f) % 1f
                drawCircle(
                    color = s.tint.copy(alpha = a * if (boost) 0.85f else 1f),
                    radius = s.radius * if (boost) 1.12f else 1f,
                    center = Offset(cx * W, s.y * H),
                )
            }

            if (!boost) {
                shootingStars.forEach { ss ->
                    val t = (((now + ss.offsetMs) % ss.periodMs) / ss.periodMs).toFloat()
                    if (t < 0.10f) {
                        val u = t / 0.10f
                        val travel = ss.length * u
                        val head = Offset(
                            (ss.startX + ss.dx * travel) * W,
                            (ss.startY + ss.dy * travel) * H,
                        )
                        val tailU = (u - 0.45f).coerceAtLeast(0f)
                        val tail = Offset(
                            (ss.startX + ss.dx * ss.length * tailU) * W,
                            (ss.startY + ss.dy * ss.length * tailU) * H,
                        )
                        val a = ss.alpha * (1f - abs(u - 0.40f) * 2.4f).coerceIn(0f, 1f)
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = a)),
                                start = tail,
                                end = head,
                            ),
                            start = tail,
                            end = head,
                            strokeWidth = 1.1.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            // Soft solar illumination wash across the card.
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFFFFC070).copy(alpha = 0.12f * pulse),
                        0.28f to Color(0xFFFF8A40).copy(alpha = 0.05f),
                        1f to Color.Transparent,
                    ),
                    center = sunPos,
                    radius = scale * 0.78f,
                ),
                radius = scale * 0.78f,
                center = sunPos,
                blendMode = BlendMode.Plus,
            )

            planets.forEach { spec ->
                drawTopDownOrbit(sunPos, spec, orbitScale, fade = if (boost) 0.40f else 1f)
            }

            val states = planets.map { spec ->
                spec to orbitalPoint(spec, now, sunPos.x, sunPos.y, orbitScale)
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
                        scale = orbitScale,
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
                    spec = spec,
                    scale = depthScale(hz) * spec.bodyScale,
                    alpha = depthAlpha(hz) * if (transiting) 0.78f else 1f,
                    transit = transiting,
                    timeMs = now,
                    textures = planetTextures,
                    atlas = planetSphereAtlas,
                )
            }

            backPlanets.forEach { (spec, pos) -> drawPlanet(spec, pos) }
            drawSunSprite(
                center = sunPos,
                diskR = sunDiskR,
                timeMs = now,
                pulse = pulse,
                texture = sunTexture,
                cache = sunSphereCache,
                spots = sunSpots,
            )
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
                        0f to Color.Black.copy(alpha = 0.16f),
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
    val samples = 96
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
        color = Color.White.copy(alpha = 0.045f * fade),
        style = Stroke(width = 0.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
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

    val baseW = 1.6.dp.toPx() * depthScale(depthZ) * spec.bodyScale.coerceAtMost(1.3f)
    for (i in 0 until pts.size - 1) {
        val frac = (i + 1).toFloat() / pts.size
        val a = frac * 0.28f * depthAlpha(depthZ) * fade
        drawLine(
            color = spec.color.copy(alpha = a),
            start = pts[i],
            end = pts[i + 1],
            strokeWidth = (baseW * frac).coerceAtLeast(0.55.dp.toPx()),
            cap = StrokeCap.Round,
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawLitPlanet(
    center: Offset,
    sunPos: Offset,
    spec: PlanetSpec,
    scale: Float,
    alpha: Float,
    transit: Boolean,
    timeMs: Double,
    textures: List<ImageBitmap>,
    atlas: PlanetSphereAtlas,
) {
    val bodyR = 5.6.dp.toPx() * scale
    val dx = sunPos.x - center.x
    val dy = sunPos.y - center.y
    val dist = hypot(dx, dy).coerceAtLeast(0.001f)
    val nx = dx / dist
    val ny = dy / dist
    val color = spec.color

    if (spec.hasRing) {
        drawSaturnRing(center, bodyR, color, alpha, front = false, nx, ny)
    }

    if (spec.atmosphereAlpha > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    spec.atmosphereColor.copy(alpha = spec.atmosphereAlpha * alpha * 0.85f),
                    Color.Transparent,
                ),
                center = center,
                radius = bodyR * 2.0f,
            ),
            radius = bodyR * 2.0f,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }

    if (transit) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.58f * alpha),
            radius = bodyR,
            center = center,
        )
    }

    val tex = textures.getOrNull(spec.index)
    if (tex != null && alpha > 0.02f) {
        val spin = (timeMs * spec.spinRate + spec.phase).toFloat()
        val sphere = atlas.image(
            index = spec.index,
            texture = tex,
            radiusPx = bodyR,
            rotationRad = spin,
            lightX = nx,
            lightY = ny,
        )
        val size = (bodyR * 2f).toInt().coerceAtLeast(1)
        // Soft alpha via overlay darkening when depth-faded.
        if (alpha >= 0.98f) {
            drawImage(
                image = sphere,
                dstOffset = IntOffset((center.x - bodyR).toInt(), (center.y - bodyR).toInt()),
                dstSize = IntSize(size, size),
            )
        } else {
            drawImage(
                image = sphere,
                dstOffset = IntOffset((center.x - bodyR).toInt(), (center.y - bodyR).toInt()),
                dstSize = IntSize(size, size),
                alpha = alpha,
            )
        }
    } else {
        // Fallback flat shading if a texture failed to load.
        drawCircle(color = spec.shadeColor.copy(alpha = 0.95f * alpha), radius = bodyR, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    spec.litColor.copy(alpha = 0.95f * alpha),
                    color.copy(alpha = 0.5f * alpha),
                    Color.Transparent,
                ),
                center = Offset(center.x + nx * bodyR * 0.35f, center.y + ny * bodyR * 0.35f),
                radius = bodyR * 1.4f,
            ),
            radius = bodyR,
            center = center,
        )
    }

    // Soft specular kiss on the day side.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.18f * alpha * spec.specular),
                Color.Transparent,
            ),
            center = Offset(center.x + nx * bodyR * 0.28f, center.y + ny * bodyR * 0.28f),
            radius = bodyR * 0.40f,
        ),
        radius = bodyR * 0.40f,
        center = Offset(center.x + nx * bodyR * 0.28f, center.y + ny * bodyR * 0.28f),
        blendMode = BlendMode.Plus,
    )

    if (spec.hasRing) {
        drawSaturnRing(center, bodyR, color, alpha, front = true, nx, ny)
    }
}

private fun DrawScope.drawSaturnRing(
    center: Offset,
    bodyR: Float,
    color: Color,
    alpha: Float,
    front: Boolean,
    nx: Float,
    ny: Float,
) {
    val ringAngle = Math.toDegrees(atan2(ny.toDouble(), nx.toDouble())).toFloat() * 0.12f
    val rx = bodyR * 2.35f
    val ry = bodyR * 0.48f
    rotate(degrees = ringAngle, pivot = center) {
        val topLeft = Offset(center.x - rx, center.y - ry)
        val size = Size(rx * 2f, ry * 2f)
        if (front) {
            // Front arc: clip to lower half of ring ellipse.
            val clip = Path().apply {
                moveTo(center.x - rx * 1.1f, center.y)
                lineTo(center.x + rx * 1.1f, center.y)
                lineTo(center.x + rx * 1.1f, center.y + ry * 1.4f)
                lineTo(center.x - rx * 1.1f, center.y + ry * 1.4f)
                close()
            }
            clipPath(clip) {
                drawOval(
                    color = Color(0xFFE8DCC0).copy(alpha = 0.42f * alpha),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round),
                )
                drawOval(
                    color = color.copy(alpha = 0.18f * alpha),
                    topLeft = Offset(center.x - rx * 0.88f, center.y - ry * 0.72f),
                    size = Size(rx * 1.76f, ry * 1.44f),
                    style = Stroke(width = 0.7.dp.toPx()),
                )
            }
        } else {
            val clip = Path().apply {
                moveTo(center.x - rx * 1.1f, center.y)
                lineTo(center.x + rx * 1.1f, center.y)
                lineTo(center.x + rx * 1.1f, center.y - ry * 1.4f)
                lineTo(center.x - rx * 1.1f, center.y - ry * 1.4f)
                close()
            }
            clipPath(clip) {
                drawOval(
                    color = Color(0xFFC8B890).copy(alpha = 0.28f * alpha),
                    topLeft = topLeft,
                    size = size,
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}
