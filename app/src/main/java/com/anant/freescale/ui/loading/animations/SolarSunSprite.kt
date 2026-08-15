package com.anant.freescale.ui.loading.animations

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anant.freescale.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val SunOrange = Color(0xFFFF8A18)
private val SunDeepOrange = Color(0xFFE85A00)
private val SunYellow = Color(0xFFFFD54A)
private val SunBrightYellow = Color(0xFFFFE082)

/**
 * Soft-matte 3D sun: keeps spherical volume/limb darkening, but no hard specular.
 * Warm yellow/orange wash with tiny orange↔yellow flickering spots.
 *
 * Base map: Solar System Scope 2k sun (CC BY 4.0).
 * See docs/ATTRIBUTION_solar_textures.txt.
 */
@Composable
internal fun rememberSunTexture(): ImageBitmap {
    val context = LocalContext.current
    return remember(context) {
        BitmapFactory.decodeResource(context.resources, R.raw.solar_sun_texture)
            .asImageBitmap()
    }
}

@Composable
internal fun rememberSunSphereCache(): SphereRasterCache = remember { SphereRasterCache() }

@Composable
internal fun rememberSunSpotSeeds(): List<SunSpotSeed> = remember {
    val rng = Random(0x51C0_5A07)
    List(36) {
        SunSpotSeed(
            ang = rng.nextFloat() * (2f * PI.toFloat()),
            dist = 0.12f + rng.nextFloat() * 0.78f,
            radius = 0.035f + rng.nextFloat() * 0.055f,
            phase = rng.nextFloat() * (2f * PI.toFloat()),
            freq = 0.0014f + rng.nextFloat() * 0.0028f,
            drift = 0.00004f + rng.nextFloat() * 0.00010f,
        )
    }
}

internal data class SunSpotSeed(
    val ang: Float,
    val dist: Float,
    val radius: Float,
    val phase: Float,
    val freq: Float,
    val drift: Float,
)

internal fun DrawScope.drawSunSprite(
    center: Offset,
    diskR: Float,
    timeMs: Double,
    pulse: Float,
    texture: ImageBitmap,
    cache: SphereRasterCache,
    spots: List<SunSpotSeed>,
) {
    val f = pulse
    val rotation = (timeMs * 0.00015).toFloat()

    drawSoftHalo(center, diskR, timeMs, f)

    // Real spherical lighting — matte, not chrome (no bright specular later).
    val sphere = cache.image(
        texture = texture,
        radiusPx = diskR,
        rotationRad = rotation,
        lightX = -0.35f,
        lightY = -0.42f,
        lightZ = 0.78f,
        nightFloor = 0.42f,
        maxPx = 220,
    )
    val size = (diskR * 2f).toInt().coerceAtLeast(1)
    drawImage(
        image = sphere,
        dstOffset = IntOffset((center.x - diskR).toInt(), (center.y - diskR).toInt()),
        dstSize = IntSize(size, size),
    )

    val diskPath = Path().apply {
        addOval(
            Rect(
                left = center.x - diskR,
                top = center.y - diskR,
                right = center.x + diskR,
                bottom = center.y + diskR,
            ),
        )
    }
    clipPath(diskPath) {
        // Gentle warm tint — yellow/orange without washing out volume.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to SunBrightYellow.copy(alpha = 0.22f * f),
                    0.55f to SunYellow.copy(alpha = 0.18f * f),
                    0.88f to SunOrange.copy(alpha = 0.16f * f),
                    1f to SunDeepOrange.copy(alpha = 0.14f * f),
                ),
                center = Offset(center.x - diskR * 0.12f, center.y - diskR * 0.14f),
                radius = diskR * 1.05f,
            ),
            radius = diskR,
            center = center,
            blendMode = BlendMode.SrcAtop,
        )

        // Tiny photosphere spots flickering orange ↔ yellow.
        spots.forEach { spot ->
            val ang = spot.ang + (timeMs * spot.drift).toFloat()
            val wave = (sin(timeMs * spot.freq + spot.phase).toFloat() + 1f) * 0.5f
            val color = lerpSunColor(SunDeepOrange, SunBrightYellow, wave)
            val c = Offset(
                center.x + cos(ang) * spot.dist * diskR * 0.92f,
                center.y + sin(ang) * spot.dist * diskR * 0.92f,
            )
            val r = diskR * spot.radius * (0.85f + 0.20f * wave)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.48f * f),
                        color.copy(alpha = 0.14f * f),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = r * 1.5f,
                ),
                radius = r * 1.5f,
                center = c,
                blendMode = BlendMode.SrcAtop,
            )
            val wave2 = 1f - wave
            val color2 = lerpSunColor(SunOrange, SunYellow, wave2)
            drawCircle(
                color = color2.copy(alpha = 0.22f * f * wave2),
                radius = r * 0.5f,
                center = Offset(c.x + r * 0.2f, c.y - r * 0.12f),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }

    // Soft chromosphere rim only — no white specular highlight.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.78f to Color.Transparent,
                0.94f to SunOrange.copy(alpha = 0.20f * f),
                1f to SunDeepOrange.copy(alpha = 0.06f * f),
            ),
            center = center,
            radius = diskR * 1.06f,
        ),
        radius = diskR * 1.06f,
        center = center,
        blendMode = BlendMode.Plus,
    )
    drawCircle(
        color = SunOrange.copy(alpha = 0.14f * f),
        radius = diskR,
        center = center,
        style = Stroke(width = 0.9.dp.toPx()),
    )
}

private fun lerpSunColor(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * u,
        green = a.green + (b.green - a.green) * u,
        blue = a.blue + (b.blue - a.blue) * u,
        alpha = 1f,
    )
}

private fun DrawScope.drawSoftHalo(
    center: Offset,
    diskR: Float,
    timeMs: Double,
    f: Float,
) {
    val breath = 0.96f + 0.04f * sin(timeMs * 0.0007).toFloat()
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to SunYellow.copy(alpha = 0.12f * f * breath),
                0.40f to SunOrange.copy(alpha = 0.055f * f),
                1f to Color.Transparent,
            ),
            center = center,
            radius = diskR * 4.2f,
        ),
        radius = diskR * 4.2f,
        center = center,
        blendMode = BlendMode.Plus,
    )
}
