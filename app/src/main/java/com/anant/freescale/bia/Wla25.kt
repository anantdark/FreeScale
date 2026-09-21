package com.anant.freescale.bia

import kotlin.math.abs

/**
 * Chipsea / ICOMON **WLA25** body composition, as used by the Fitdays / ICOMON
 * 8-electrode scale family that the Dr. Trust SSW532 (FG2211WB) belongs to.
 *
 * Ported from `ICBodyFatAlgorithmWLA25::calc` in the vendor
 * `libICBodyFatAlgorithms.so`, cross-checked against openScale's
 * [`Wla25BodyComposition`](https://github.com/oliexdev/openScale/blob/master/android_app/app/src/main/java/com/health/openscale/core/bluetooth/libs/Wla25BodyComposition.kt)
 * (GPL-3.0) and [sacoma-lib](https://github.com/ynsgnr/sacoma-lib) `sacoma/wla25.py`.
 *
 * ## Structure
 *
 * The algorithm is really two independent halves:
 *
 *  1. **A body-fat regression** over the ten measured impedances
 *     ([fatMassFromImpedances]). On SSW532 hardware this half does not work;
 *     see its KDoc.
 *  2. **A derivation chain** ([derive]) in which every remaining metric falls
 *     out of fat-free mass alone. This half is exact. Driven with the vendor
 *     app's own body-fat figure it reproduces all 18 values the vendor app
 *     displays, to the last digit — see `Wla25Test`.
 *
 * So this app keeps the chain and sources body fat from [Ssw532FatModel]
 * instead of the regression.
 *
 * ## Load-bearing details
 *
 * Rounding order matters — each step is worth a tenth of a unit. Fat mass is
 * rounded before fat-free mass is taken from it, and the BMI is rounded for
 * display. [round1] is half-up in single precision because the vendor's `fmodf`
 * chain is.
 *
 * One deliberate departure: the vendor also rounds *weight* to one decimal before
 * anything else. [derive] uses the exact weight the scale reported instead, so a
 * 71.15 kg weigh-in is not silently treated as 71.2. That has no effect on the
 * reference weigh-in used to validate the chain (71.20 kg is already one decimal)
 * and it keeps the finer weight visible in the derived kg figures.
 */
object Wla25 {

    /** Body fat is clamped to this range before anything is derived from it. */
    private const val BFR_MIN = 3.0
    private const val BFR_MAX = 60.0

    /** Reference BMI the vendor uses for "standard weight". Indexed male/female. */
    private const val STD_BMI_MALE = 22.0
    private const val STD_BMI_FEMALE = 21.0

    /** Fraction of standard weight that is fat-free / fat. Indexed [female, male]. */
    private val FFM_FACTOR = doubleArrayOf(0.77, 0.85)
    private val BFM_FACTOR = doubleArrayOf(0.23, 0.15)
    private val SCORE_CORR = doubleArrayOf(-0.958, 0.983)

    /**
     * The vendor's validity gate. Slots 0 and 5 lead each of the two groups of
     * five and are small; the other eight are ~300 ohm.
     */
    private val IMP_MIN = doubleArrayOf(1.0, 100.0, 100.0, 100.0, 100.0, 1.0, 100.0, 100.0, 100.0, 100.0)

    data class Result(
        val bmi: Double,
        val bodyFatPercent: Double,
        val musclePercent: Double,
        val subcutaneousFatPercent: Double,
        val visceralFat: Double,
        val boneMassKg: Double,
        val bodyWaterPercent: Double,
        val proteinPercent: Double,
        val skeletalMusclePercent: Double,
        val bmrKcal: Int,
        val metabolicAge: Int,
        val bodyScore: Double,
        val fatMassKg: Double,
        val leanMassKg: Double,
        /** Standard weight for the height: the vendor's reference, and our ideal weight. */
        val standardWeightKg: Double,
        /** Segmental fat/muscle for LA, RA, LL, RL, trunk. Empty when impedances are unusable. */
        val segments: List<Segment> = emptyList(),
    )

    data class Segment(
        val name: String,
        val fatKg: Double,
        val fatPct: Double,
        val muscleKg: Double,
        val musclePct: Double,
    )

    /** Coerce to IEEE-754 binary32, as the native library does. */
    private fun f32(x: Double): Double = x.toFloat().toDouble()

    /**
     * The vendor's one-decimal rounding: half-up, computed in float32.
     *
     * Both properties matter. `round1(26.35)` is 26.4 where half-to-even would
     * give 26.3; and 1.95 has a float32 fraction of exactly 0.95, so the half-up
     * test fails and the answer is 1.9 rather than 2.0.
     */
    fun round1(value: Double): Double {
        val v = value.toFloat()
        val whole = v.toInt()
        val tenths = (v % 1.0f) * 10.0f
        val carried = if (tenths % 1.0f > 0.5f) tenths + 1.0f else tenths
        return (carried.toInt() / 10.0f + whole).toDouble()
    }

    fun bmi(heightCm: Int, weightKg: Double): Double = weightKg * 10000.0 / (heightCm * heightCm)

    private fun stdBmi(sexMale1: Int): Double =
        if (sexMale1 == 1) STD_BMI_MALE else STD_BMI_FEMALE

    /**
     * Vendor "standard weight": reference BMI times height squared.
     *
     * This is also the ideal weight the vendor app displays — 67.4 kg at 175 cm
     * for a male, which is `22 * 1.75^2`. Note it is *not* Broca (`height - 100`),
     * which would give 75 kg and does not match the vendor app.
     */
    fun standardWeightKg(heightCm: Double, sexMale1: Int): Double {
        val h = f32(heightCm / 100.0)
        return f32(h * h * f32(stdBmi(sexMale1)))
    }

    private fun standardFfmKg(heightCm: Double, sexMale1: Int): Double =
        f32(FFM_FACTOR[sexMale1] * standardWeightKg(heightCm, sexMale1))

    private fun standardBfmKg(heightCm: Double, sexMale1: Int): Double =
        f32(BFM_FACTOR[sexMale1] * standardWeightKg(heightCm, sexMale1))

    fun impedancesValid(imps: DoubleArray?): Boolean {
        if (imps == null || imps.size != 10) return false
        return imps.indices.none { imps[it] < IMP_MIN[it] }
    }

    /**
     * The vendor's 13-term fat-mass regression over height, weight, rounded BMI
     * and all ten impedances.
     *
     * ## Not usable on the SSW532 — kept for reference
     *
     * The regression wants the ten resistances in the wire order the Fitdays /
     * Relaxmedic frames deliver them: two groups of five, each led by a small
     * (~15-25 ohm) value. The SSW532 does not report that set. It gives two
     * whole-body channels A and B plus eight segmental values Z1-Z8 whose
     * semantics differ, and its two sub-100 values (Z3, Z8) swing over
     * 10-49 ohm and 2.6-25 ohm between weigh-ins of the same person.
     *
     * Slot 5 carries a coefficient of 0.439 after a 0.826 scaling, so a 40 ohm
     * swing there alone moves fat mass by ~14 kg. That is what made this app
     * report 14.4-30.6 % body fat across 15 readings of a person the vendor app
     * had steady at 14.6 %.
     *
     * This is not a mis-ordering that can be corrected. All 80,640 slot
     * assignments that satisfy the gate were enumerated against those 15
     * readings; the best achievable spread was 9.2 pp, against 1.7 pp for
     * [Ssw532FatModel]. The inputs are the wrong quantities, not the right ones
     * in the wrong order.
     *
     * openScale reaches the same conclusion by a different route: its
     * `Wla25BodyComposition` is wired to the Relaxmedic handler, while its
     * SSW532 handler uses `StandardImpedanceLib` over the foot-to-foot path.
     *
     * @param weightKg must already be rounded by [round1].
     */
    fun fatMassFromImpedances(heightCm: Int, weightKg: Double, imps: DoubleArray): Double {
        val scaled0 = imps[0] * 0.826
        val scaled5 = if (imps[5] <= imps[0]) imps[5] * 0.826 else scaled0 - 3.0
        return weightKg * -0.138 +
            heightCm * 0.164 +
            round1(bmi(heightCm, weightKg)) * 2.657 +
            imps[2] * -0.053 +
            imps[1] * -0.000491 +
            scaled0 * -0.03 +
            imps[4] * -0.127 +
            imps[3] * -0.052 +
            imps[7] * 0.07 +
            imps[6] * 0.019 +
            scaled5 * 0.439 +
            imps[9] * 0.153 +
            imps[8] * 0.07 +
            -88.052
    }

    /**
     * Derive the full body composition from a body-fat percentage.
     *
     * Everything here follows from fat-free mass; the only measured inputs are
     * weight, height, age, sex and [bodyFatPercentRaw]. Verified against the
     * vendor app on all 18 displayed metrics.
     *
     * @param bodyFatPercentRaw body fat as a percentage of body weight, unrounded
     *   and unclamped. From [Ssw532FatModel] on this hardware.
     * @param imps optional ten-slot impedance vector, used only for the
     *   segmental breakdown. Pass `null` to skip it.
     */
    fun derive(
        heightCm: Int,
        rawWeightKg: Double,
        age: Int,
        sexMale1: Int,
        bodyFatPercentRaw: Double,
        imps: DoubleArray? = null,
    ): Result {
        // The vendor rounds weight to one decimal here. We use it exactly as the
        // scale reported it, so 71.15 kg stays 71.15 rather than becoming 71.2.
        // This changes nothing on the reference weigh-in (71.20 is already one
        // decimal) and keeps the finer weight visible in every derived kg figure.
        // Outputs are still rounded one-decimal, which is what the vendor displays.
        val weight = rawWeightKg
        val pct = bodyFatPercentRaw.coerceIn(BFR_MIN, BFR_MAX)

        // Fat mass is rounded before fat-free mass is taken from it.
        val fatMass = round1(pct / 100.0 * weight)
        val lean = weight - fatMass
        val waterMass = lean * 0.733

        val bfr = round1(pct)
        val musclePct = round1((lean * 0.733 + lean * 0.2) / weight * 100.0)
        val waterPct = round1(waterMass / weight * 100.0)

        // Visceral fat is an int cast, not a rounding, and is a 1..20 level.
        val visceral = (lean * -0.029 + fatMass * 0.502 - 0.477).toInt().coerceIn(1, 20)

        return Result(
            bmi = round1(bmi(heightCm, weight)),
            bodyFatPercent = bfr,
            musclePercent = musclePct,
            subcutaneousFatPercent = round1((bfr * -0.0002 + 0.72) * bfr),
            visceralFat = visceral.toDouble(),
            boneMassKg = round1(lean * 0.067),
            bodyWaterPercent = waterPct,
            proteinPercent = round1(lean * 0.2 / weight * 100.0),
            skeletalMusclePercent = round1((waterMass * 0.834 - 2.627) / weight * 100.0),
            bmrKcal = (lean * 21.6 + 370.0).toInt(),
            metabolicAge = bodyAge(age, bfr, sexMale1),
            bodyScore = bodyScore(heightCm.toDouble(), weight, sexMale1, bfr).toDouble(),
            fatMassKg = fatMass,
            leanMassKg = lean,
            standardWeightKg = standardWeightKg(heightCm.toDouble(), sexMale1),
            segments = if (impedancesValid(imps)) {
                segments(heightCm.toDouble(), weight, sexMale1, fatMass, lean, imps!!)
            } else {
                emptyList()
            },
        )
    }

    /**
     * Body score out of 100: 80, plus how far fat-free mass exceeds the
     * reference for the height, minus a correction on the fat residual.
     */
    fun bodyScore(heightCm: Double, weightKg: Double, sexMale1: Int, bodyFatPercent: Double): Int {
        val sw = standardWeightKg(heightCm, sexMale1)
        val fatKg = f32(bodyFatPercent / 100.0 * weightKg)
        val resid = f32(fatKg - f32(BFM_FACTOR[sexMale1] * sw))
        val corr = SCORE_CORR[if (resid < 0.0) 1 else 0]
        val score = ((weightKg - fatKg) - f32(FFM_FACTOR[sexMale1] * sw) + 80.0 + corr * resid).toInt()
        return if (score < 21) 20 else score
    }

    /**
     * Metabolic age: chronological age nudged by a per-sex body-fat band.
     *
     * The offsets skip zero — the healthy band steps straight from -1 to +1. The
     * female band at `[45, 46)` returning +0 while `>= 46` gives +5 is not a
     * transcription slip; the vendor library really does single it out.
     */
    fun bodyAge(age: Int, bodyFatPercent: Double, sexMale1: Int): Int {
        if (age < 10) return age
        val bands = if (sexMale1 == 1) {
            arrayOf(14.0 to -3, 19.0 to -2, 24.0 to -1, 27.0 to 1, 30.0 to 2, 33.0 to 3, 36.0 to 4)
        } else {
            arrayOf(24.0 to -3, 28.0 to -2, 32.0 to -1, 35.0 to 1, 38.0 to 2, 42.0 to 3, 45.0 to 4, 46.0 to 0)
        }
        for ((upper, delta) in bands) {
            if (bodyFatPercent < upper) return age + delta
        }
        return age + 5
    }

    /**
     * Segmental fat and muscle for the five sites.
     *
     * Mostly a function of whole-body fat and lean mass, with a small impedance
     * correction per site. Because the SSW532 slot mapping is unverified (see
     * [fatMassFromImpedances]) the correction term is best-effort: these five
     * rows are indicative, unlike the whole-body metrics from [derive]. The
     * vendor app does not display them, so there is nothing to check against.
     *
     * The left/right reconciliation and the 0.1/0.2/0.7 kg floors below are
     * verbatim from the vendor code, including the three different divisors
     * (20213, 20203, 20113) — not a typo introduced here.
     */
    private fun segments(
        heightCm: Double,
        weightKg: Double,
        sexMale1: Int,
        fatMass: Double,
        lean: Double,
        imps: DoubleArray,
    ): List<Segment> {
        val trunkZ = imps[0] * 0.826
        val trunkZ2 = if (imps[5] <= imps[0]) imps[5] * 0.826 else trunkZ - 3.0
        val zLa = imps[1]
        val zRa = imps[2]
        val zLl = imps[3]
        val zRl = imps[4]
        val chB = imps[6]
        val chA = imps[7]
        val zX1 = imps[8]
        val zX2 = imps[9]

        val refBfm = round1(standardBfmKg(heightCm, sexMale1))
        val refFfm = round1(standardFfmKg(heightCm, sexMale1))

        var laFat = chB * 0.007476 + (fatMass * 0.081201 - zLa * 0.005752) - 0.662152
        var raFat = chA * 0.007476 + (fatMass * 0.081201 - zRa * 0.005752) - 0.662152
        var rlFat = zX2 * 0.008645 + (fatMass * 0.135438 - zRl * 0.00801) + 0.492479
        var llFat = zX1 * 0.008645 + (fatMass * 0.135438 - zLl * 0.00801) + 0.492479

        // Reconcile implausible left/right asymmetry.
        if (abs(raFat - laFat) > 0.3) {
            if (raFat <= laFat) {
                val t = (zRa + chA) / 20213.0
                raFat = (if (zRa <= zLa) t else -t) + laFat
            } else {
                val t = (zLa + chB) / 20213.0
                laFat = (if (zLa <= zRa) t else -t) + raFat
            }
        }
        if (abs(rlFat - llFat) > 0.5) {
            if (rlFat <= llFat) {
                val t = (zRl + zX2) / 20213.0
                rlFat = (if (zRl <= zLl) t else -t) + llFat
            } else {
                val t = (zLl + zX1) / 20213.0
                llFat = (if (zLl <= zRl) t else -t) + rlFat
            }
        }

        var trunkFat = trunkZ * 0.068621 + fatMass * 0.552545 + trunkZ2 * -0.131612 + 0.322704
        var raMus = ((zRa * 0.002847 + lean * 0.058707) - chA * 0.005857) + 0.561911
        var laMus = ((zLa * 0.002847 + lean * 0.058707) - chB * 0.005857) + 0.561911
        var trunkMus = trunkZ * 0.005246 + lean * 0.440922 + trunkZ2 * -0.010469 - 0.275461
        var rlMus = zX2 * 0.008157 + (lean * 0.176554 - zRl * 0.007381) - 0.688932
        var llMus = zX1 * 0.008157 + (lean * 0.176554 - zLl * 0.007381) - 0.688932

        if (raFat < 0.1) raFat = (zRa + chA) / 20213.0 + 0.1
        if (laFat < 0.1) laFat = (zLa + chB) / 20113.0 + 0.1
        if (trunkFat < 0.1) trunkFat = (trunkZ2 + trunkZ) / 20203.0 + 0.1
        if (rlFat < 0.1) rlFat = (zRl + zX2) / 20213.0 + 0.1
        if (llFat < 0.1) llFat = (zLl + zX1) / 20113.0 + 0.1
        if (raMus < 0.2) raMus = (zRa + chA) / 20213.0 + 0.2
        if (laMus < 0.2) laMus = (zLa + chB) / 20113.0 + 0.2
        if (trunkMus < 0.7) trunkMus = (trunkZ2 + trunkZ) / 20203.0 + 0.7
        if (rlMus < 0.2) rlMus = (zRl + zX2) / 20213.0 + 0.2
        if (llMus < 0.2) llMus = (zLl + zX1) / 20113.0 + 0.2

        // Reference masses the vendor compares each site against.
        val armMusRef = weightKg * 0.02 + refFfm * 0.102 + heightCm * -0.045 + 3.752
        val legMusRef = weightKg * 0.059 + refFfm * 0.168 + heightCm * -0.056 + 4.775
        val armFatRef = refBfm * 0.101 + heightCm * -0.004 + 0.331
        val legFatRef = refBfm * 0.215 + heightCm * -0.005 + 0.391
        val trunkFatRef = heightCm * 0.006 + refBfm * 0.389 - 0.683
        val trunkMusRef = weightKg * 0.166 + refFfm * 0.485 + heightCm * -0.16 + 13.595

        fun pct(v: Double, ref: Double) = if (ref > 0.0) v / ref * 100.0 else 0.0

        return listOf(
            Segment("Left arm", laFat, pct(laFat, armFatRef), laMus, pct(laMus, armMusRef)),
            Segment("Right arm", raFat, pct(raFat, armFatRef), raMus, pct(raMus, armMusRef)),
            Segment("Trunk", trunkFat, pct(trunkFat, trunkFatRef), trunkMus, pct(trunkMus, trunkMusRef)),
            Segment("Left leg", llFat, pct(llFat, legFatRef), llMus, pct(llMus, legMusRef)),
            Segment("Right leg", rlFat, pct(rlFat, legFatRef), rlMus, pct(rlMus, legMusRef)),
        )
    }
}

/**
 * Map SSW532 pkt0 channels A/B + pkt1 Z1-Z8 onto WLA25's ten-slot vector.
 *
 * **This ordering is unverified and only feeds the segmental breakdown.** The
 * whole-body metrics no longer depend on it: body fat comes from
 * [Ssw532FatModel] and everything else from [Wla25.derive]. See
 * [Wla25.fatMassFromImpedances] for why the vector cannot be trusted for the
 * fat regression.
 *
 * Slots 0 and 5 take the two sub-100 ohm values, which on FG2211WB are Z3 and Z8.
 */
object Ssw532ImpedanceMap {
    fun toWla25(
        channelAOhm: Double,
        channelBOhm: Double,
        z1to8: List<Double>,
    ): DoubleArray? {
        if (z1to8.size < 8) return null
        val z = z1to8
        return doubleArrayOf(
            z[2], // Z3 trunk (small)
            z[0], // Z1 arm path
            z[1], // Z2 arm path
            z[3], // Z4 right leg
            z[4], // Z5 left leg
            z[7], // Z8 trunk/path (small)
            channelBOhm,
            channelAOhm,
            z[5], // Z6 cross-body
            z[6], // Z7 cross-body
        )
    }
}
