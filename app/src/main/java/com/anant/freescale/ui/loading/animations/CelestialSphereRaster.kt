package com.anant.freescale.ui.loading.animations

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.anant.freescale.R
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Solar System Scope planet maps (CC BY 4.0) — same library as the sun texture.
 * https://www.solarsystemscope.com/textures/
 */
internal val PlanetTextureResIds: IntArray = intArrayOf(
    R.raw.solar_mercury,
    R.raw.solar_venus,
    R.raw.solar_earth,
    R.raw.solar_mars,
    R.raw.solar_jupiter,
    R.raw.solar_saturn,
    R.raw.solar_uranus,
    R.raw.solar_neptune,
)

@Composable
internal fun rememberPlanetTextures(): List<ImageBitmap> {
    val context = LocalContext.current
    return remember(context) {
        PlanetTextureResIds.map { id ->
            BitmapFactory.decodeResource(context.resources, id).asImageBitmap()
        }
    }
}

@Composable
internal fun rememberPlanetSphereAtlas(): PlanetSphereAtlas =
    remember { PlanetSphereAtlas(PlanetTextureResIds.size) }

/** Per-planet sphere raster cache. */
internal class PlanetSphereAtlas(count: Int) {
    private val caches = Array(count) { SphereRasterCache() }

    fun image(
        index: Int,
        texture: ImageBitmap,
        radiusPx: Float,
        rotationRad: Float,
        lightX: Float,
        lightY: Float,
    ): ImageBitmap =
        caches[index.coerceIn(0, caches.lastIndex)].image(
            texture = texture,
            radiusPx = radiusPx,
            rotationRad = rotationRad,
            lightX = lightX,
            lightY = lightY,
            lightZ = 0.62f,
            nightFloor = 0.12f,
            maxPx = 120,
        )
}

/**
 * Shared equirectangular → orthographic sphere rasterizer used by the sun and planets.
 */
internal class SphereRasterCache {
    private var buffer: Bitmap? = null
    private var lastPx = 0
    private var lastRotQuant = Int.MIN_VALUE
    private var lastLightQuant = Int.MIN_VALUE
    private var lastImage: ImageBitmap? = null

    fun image(
        texture: ImageBitmap,
        radiusPx: Float,
        rotationRad: Float,
        lightX: Float = -0.45f,
        lightY: Float = -0.55f,
        lightZ: Float = 0.70f,
        nightFloor: Float = 0.28f,
        maxPx: Int = 220,
    ): ImageBitmap {
        val px = (radiusPx * 2f).toInt().coerceIn(48, maxPx)
        val rotQuant = floor(rotationRad * 36f).toInt()
        val lightQuant = floor(lightX * 24f).toInt() * 1000 + floor(lightY * 24f).toInt()
        val existing = buffer
        if (
            existing != null &&
            lastPx == px &&
            lastRotQuant == rotQuant &&
            lastLightQuant == lightQuant &&
            lastImage === texture
        ) {
            return existing.asImageBitmap()
        }

        val bmp = if (existing != null && existing.width == px && existing.height == px) {
            existing
        } else {
            existing?.recycle()
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { buffer = it }
        }

        rasterizeTexturedSphere(
            dest = bmp,
            texture = texture.asAndroidBitmap(),
            rotationRad = rotationRad,
            lightX = lightX,
            lightY = lightY,
            lightZ = lightZ,
            nightFloor = nightFloor,
        )
        lastPx = px
        lastRotQuant = rotQuant
        lastLightQuant = lightQuant
        lastImage = texture
        return bmp.asImageBitmap()
    }
}

internal fun rasterizeTexturedSphere(
    dest: Bitmap,
    texture: Bitmap,
    rotationRad: Float,
    lightX: Float,
    lightY: Float,
    lightZ: Float,
    nightFloor: Float,
) {
    val px = dest.width
    val cx = (px - 1) * 0.5f
    val r = cx
    val tw = texture.width
    val th = texture.height
    val cosR = cos(rotationRad)
    val sinR = sin(rotationRad)
    val lLen = sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ).coerceAtLeast(0.001f)
    val lx = lightX / lLen
    val ly = lightY / lLen
    val lz = lightZ / lLen
    val pixels = IntArray(px * px)

    for (y in 0 until px) {
        val ny = (y - cx) / r
        for (x in 0 until px) {
            val nx = (x - cx) / r
            val d2 = nx * nx + ny * ny
            if (d2 > 1f) {
                pixels[y * px + x] = 0
                continue
            }
            val nz = sqrt(1f - d2)
            val rx = nx * cosR + nz * sinR
            val rz = -nx * sinR + nz * cosR
            val u = (0.5 + atan2(rx.toDouble(), rz.toDouble()) / (2.0 * PI)).toFloat()
            val v = (0.5 - asin(ny.toDouble().coerceIn(-1.0, 1.0)) / PI).toFloat()
            val tx = (((u % 1f) + 1f) % 1f * (tw - 1)).toInt().coerceIn(0, tw - 1)
            val ty = (v.coerceIn(0f, 1f) * (th - 1)).toInt().coerceIn(0, th - 1)
            val sample = texture.getPixel(tx, ty)

            val ndotl = (nx * lx + ny * ly + nz * lz).coerceIn(0f, 1f)
            val limb = nightFloor + (1f - nightFloor) * nz
            val shade = (0.22f + 0.78f * ndotl) * limb

            val a = sample ushr 24 and 0xFF
            val sr = sample shr 16 and 0xFF
            val sg = sample shr 8 and 0xFF
            val sb = sample and 0xFF
            val rr = (sr * shade).toInt().coerceIn(0, 255)
            val gg = (sg * shade).toInt().coerceIn(0, 255)
            val bb = (sb * shade).toInt().coerceIn(0, 255)
            pixels[y * px + x] = (a shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }
    dest.setPixels(pixels, 0, px, 0, 0, px, px)
}
