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
 */
internal data class PlanetSpec(
    val orbitA: Float,
    val orbitB: Float,
    val angularVel: Double,
    val phase: Double,
    val color: Color,
    val bodyScale: Float = 1f,
    val hasRing: Boolean = false,
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
    val hasRing: Boolean = false,
)

/**
 * Eight planets sized for a full instrument card. Orbits fan out from a
 * corner-anchored sun so the system sweeps the readout without crowding text.
 */
internal fun solarSystemPlanets(protein: Color, carbs: Color, fats: Color): List<PlanetSpec> {
    val looks = listOf(
        BodyLook(0.55f, 0.40), // Mercury — protein
        BodyLook(0.78f, 1.90), // Venus — carbs
        BodyLook(0.82f, 3.20), // Earth — fats
        BodyLook(0.62f, 4.60), // Mars
        BodyLook(1.65f, 5.80), // Jupiter
        BodyLook(1.40f, 1.10, hasRing = true), // Saturn
        BodyLook(1.10f, 2.70), // Uranus
        BodyLook(1.05f, 4.90), // Neptune
    )
    val colors = listOf(
        protein,
        carbs,
        fats,
        Color(0xFFE57373),
        Color(0xFFFFCC80),
        Color(0xFFFFE082),
        Color(0xFF80DEEA),
        Color(0xFF64B5F6),
    )

    // Compress AU so Neptune reaches ~0.95 of the short-side scale unit.
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
            color = colors[i],
            bodyScale = look.bodyScale,
            hasRing = look.hasRing,
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
