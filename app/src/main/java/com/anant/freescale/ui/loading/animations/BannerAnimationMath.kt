package com.anant.freescale.ui.loading.animations

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------
 // Data model
 // ---------------------------------------------------------------------------

/**
 * Elliptical Keplerian orbit around a fixed sun (top-down observatory view).
 *
 * - [orbitA] / [orbitB]: semi-axes as a fraction of the card's shorter side
 * - [angularVel]: rad/ms — strictly decreasing inner → outer (Kepler)
 * - [bodyScale]: relative planet disk size
 * - [hasRing]: faint Saturn-style ring
 * - [litColor] / [shadeColor]: day / night limb tones for terminator lighting
 * - [atmosphereColor] / [atmosphereAlpha]: soft albedo glow (0 = none)
 * - [bandCount]: gas-giant banding stripes
 * - [specular]: day-side highlight strength (0–1)
 */
internal data class PlanetSpec(
    val orbitA: Float,
    val orbitB: Float,
    val angularVel: Double,
    val phase: Double,
    val color: Color,
    val bodyScale: Float = 1f,
    val hasRing: Boolean = false,
    val litColor: Color = color,
    val shadeColor: Color = color,
    val atmosphereColor: Color = color,
    val atmosphereAlpha: Float = 0f,
    val bandCount: Int = 0,
    val specular: Float = 0.35f,
    val index: Int = 0,
    /** Spin rate in rad per scaled ms — slower outer worlds. */
    val spinRate: Float = 0.0012f,
)

// ---------------------------------------------------------------------------
 // Scaled-time accumulator
 // ---------------------------------------------------------------------------

internal fun accumulateScaledTime(prev: Double, rawDelta: Long, speed: Float): Double =
    prev + rawDelta.coerceAtLeast(0L) * speed.toDouble()

/** Calm default playback for full-card orbits. */
internal const val BANNER_SPEED_NATURAL: Float = 0.18f

/** Press-and-hold playback — sped-up but still readable. */
internal const val BANNER_SPEED_BOOST: Float = 0.55f

/** Reference period used to anchor Kepler ω (no traveling sun). */
internal const val ORBIT_TEMPO_MS: Double = 4_400.0

internal fun theta(spec: PlanetSpec, tMs: Double): Double =
    spec.phase + spec.angularVel * tMs

/**
 * Planet position on a top-down ellipse around a fixed sun.
 *
 * Returns Triple(x, y, z):
 *   x/y — canvas coords
 *   z   — depth cue ∈ [-1, 1]; z ≥ 0 is nearer the camera (slight tip)
 */
internal fun orbitalPoint(
    spec: PlanetSpec,
    tMs: Double,
    sx: Float,
    sy: Float,
    scale: Float,
): Triple<Float, Float, Float> {
    val th = theta(spec, tMs)
    val cosT = cos(th).toFloat()
    val sinT = sin(th).toFloat()
    val a = spec.orbitA * scale
    val b = spec.orbitB * scale
    // Mild perspective tip so orbits read as disks, not flat rings.
    val z = sinT
    val foreshorten = 0.94f + 0.06f * ((z + 1f) / 2f)
    val x = sx + a * cosT * foreshorten
    val y = sy + b * sinT
    return Triple(x, y, z)
}

// ---------------------------------------------------------------------------
 // Depth mappings
 // ---------------------------------------------------------------------------

internal fun depthScale(z: Float, min: Float = 0.78f, max: Float = 1.12f): Float =
    min + (max - min) * ((z + 1f) / 2f)

internal fun depthAlpha(z: Float, min: Float = 0.55f): Float =
    min + (1f - min) * ((z + 1f) / 2f)

// ---------------------------------------------------------------------------
 // Draw-order partition
 // ---------------------------------------------------------------------------

internal fun orderByDepth(
    states: List<Pair<PlanetSpec, Triple<Float, Float, Float>>>,
): Pair<
    List<Pair<PlanetSpec, Triple<Float, Float, Float>>>,
    List<Pair<PlanetSpec, Triple<Float, Float, Float>>>,
    > {
    val back = states.filter { it.second.third < 0f }.sortedBy { it.second.third }
    val front = states.filter { it.second.third >= 0f }.sortedBy { it.second.third }
    return Pair(back, front)
}

// ---------------------------------------------------------------------------
 // Real solar-system composition (compressed AU spacing, true period ratios)
 // ---------------------------------------------------------------------------

private val PLANET_AU = doubleArrayOf(
    0.387, // Mercury
    0.723, // Venus
    1.000, // Earth
    1.524, // Mars
    5.203, // Jupiter
    9.537, // Saturn
    19.191, // Uranus
    30.069, // Neptune
)

internal val PLANET_PERIOD_YEARS = doubleArrayOf(
    0.2408467,
    0.6151973,
    1.0000174,
    1.8808476,
    11.862615,
    29.447498,
    84.016846,
    164.79132,
)

/** Orbits Neptune completes during one [ORBIT_TEMPO_MS] window. */
internal const val SLOWEST_ORBITS_PER_TEMPO: Double = 0.09

private data class BodyLook(
    val bodyScale: Float,
    val phase: Double,
    val color: Color,
    val litColor: Color,
    val shadeColor: Color,
    val atmosphereColor: Color = color,
    val atmosphereAlpha: Float = 0f,
    val hasRing: Boolean = false,
    val bandCount: Int = 0,
    val specular: Float = 0.30f,
)

/**
 * Eight planets sized for a full instrument card. Orbits fan out from a
 * corner-anchored sun so the system sweeps the readout without crowding text.
 * Colors lean astronomical rather than branding pastels.
 */
internal fun solarSystemPlanets(): List<PlanetSpec> {
    val looks = listOf(
        // Mercury — cratered grey rock
        BodyLook(
            bodyScale = 0.72f,
            phase = 0.40,
            color = Color(0xFF9A9590),
            litColor = Color(0xFFD0CBC4),
            shadeColor = Color(0xFF4A4642),
            specular = 0.18f,
        ),
        // Venus — thick creamy atmosphere
        BodyLook(
            bodyScale = 1.00f,
            phase = 1.90,
            color = Color(0xFFE8C878),
            litColor = Color(0xFFFFF0C0),
            shadeColor = Color(0xFF8A6A30),
            atmosphereColor = Color(0xFFFFE0A0),
            atmosphereAlpha = 0.22f,
            specular = 0.40f,
        ),
        // Earth — ocean blue with soft atmospheric rim
        BodyLook(
            bodyScale = 1.08f,
            phase = 3.20,
            color = Color(0xFF3A7AC8),
            litColor = Color(0xFF7EC8F0),
            shadeColor = Color(0xFF0A2848),
            atmosphereColor = Color(0xFF80C8FF),
            atmosphereAlpha = 0.28f,
            specular = 0.45f,
        ),
        // Mars — rusty desert
        BodyLook(
            bodyScale = 0.82f,
            phase = 4.60,
            color = Color(0xFFC05A3A),
            litColor = Color(0xFFE88860),
            shadeColor = Color(0xFF5A2010),
            atmosphereColor = Color(0xFFE07050),
            atmosphereAlpha = 0.10f,
            specular = 0.22f,
        ),
        // Jupiter — banded gas giant
        BodyLook(
            bodyScale = 2.15f,
            phase = 5.80,
            color = Color(0xFFD4A878),
            litColor = Color(0xFFF0D0A0),
            shadeColor = Color(0xFF6A4830),
            atmosphereColor = Color(0xFFE8C090),
            atmosphereAlpha = 0.14f,
            bandCount = 0,
            specular = 0.28f,
        ),
        // Saturn — pale gold + rings
        BodyLook(
            bodyScale = 1.85f,
            phase = 1.10,
            color = Color(0xFFE0C890),
            litColor = Color(0xFFF8E8C0),
            shadeColor = Color(0xFF7A6840),
            atmosphereColor = Color(0xFFE8D8A8),
            atmosphereAlpha = 0.12f,
            hasRing = true,
            bandCount = 0,
            specular = 0.32f,
        ),
        // Uranus — ice cyan
        BodyLook(
            bodyScale = 1.40f,
            phase = 2.70,
            color = Color(0xFF7EC8D8),
            litColor = Color(0xFFB0E8F0),
            shadeColor = Color(0xFF286070),
            atmosphereColor = Color(0xFF90D8E8),
            atmosphereAlpha = 0.16f,
            specular = 0.38f,
        ),
        // Neptune — deep azure
        BodyLook(
            bodyScale = 1.32f,
            phase = 4.90,
            color = Color(0xFF3A6AD0),
            litColor = Color(0xFF70A0F0),
            shadeColor = Color(0xFF102858),
            atmosphereColor = Color(0xFF5080E0),
            atmosphereAlpha = 0.18f,
            specular = 0.42f,
        ),
    )

    // Compress AU so Neptune reaches ~0.95 of the diagonal orbit unit
    // (sun → opposite corner), tracking near the card diagonal.
    val compress = 0.42
    val raw = PLANET_AU.map { it.pow(compress) }
    val maxRaw = raw.last()
    val orbitScale = 0.95 / maxRaw

    val pSlowest = PLANET_PERIOD_YEARS.last()
    val omegaSlowest = SLOWEST_ORBITS_PER_TEMPO * 2.0 * PI / ORBIT_TEMPO_MS

    return looks.mapIndexed { i, look ->
        val orbitA = (raw[i] * orbitScale).toFloat()
        // Near-circular top-down ellipses (slight tip).
        val orbitB = orbitA * (0.88f + 0.04f * (i / 7f))
        val omega = omegaSlowest * (pSlowest / PLANET_PERIOD_YEARS[i])
        PlanetSpec(
            orbitA = orbitA,
            orbitB = orbitB,
            angularVel = omega,
            phase = look.phase,
            color = look.color,
            bodyScale = look.bodyScale,
            hasRing = look.hasRing,
            litColor = look.litColor,
            shadeColor = look.shadeColor,
            atmosphereColor = look.atmosphereColor,
            atmosphereAlpha = look.atmosphereAlpha,
            bandCount = look.bandCount,
            specular = look.specular,
            index = i,
            // Inner worlds spin faster; gas giants a bit slower visually.
            spinRate = (0.0018f - i * 0.00012f).coerceAtLeast(0.00055f),
        )
    }
}

internal val BannerProteinColor = Color(0xFFEF9A9A)
internal val BannerCarbsColor = Color(0xFF80CBC4)
internal val BannerFatsColor = Color(0xFFFFCC80)

internal val measuringCaptions: List<String> = listOf(
    "locking the readout…",
    "sampling the load cells…",
    "steadying the weight…",
    "reading impedance…",
    "crunching body comp…",
)

internal fun measuringLabel(status: String?): String =
    if (!status.isNullOrBlank()) status.uppercase() else "MEASURING"
