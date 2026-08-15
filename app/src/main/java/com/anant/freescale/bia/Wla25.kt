package com.anant.freescale.bia

import kotlin.math.abs
import kotlin.math.truncate

/**
 * Pure-Kotlin port of Chipsea / ICOMON `ICBodyFatAlgorithmWLA25::calc`.
 *
 * Source: [sacoma-lib](https://github.com/ynsgnr/sacoma-lib) `sacoma/wla25.py` (MIT),
 * a bit-exact reverse of the vendor library used by Fitdays / ICOMON 8-electrode scales.
 * Dr. Trust SSW532 (FG2211WB) is the same OEM family.
 *
 * Input: weight kg, height cm, sex (1=male, 0=female), age, peopleType (0=normal),
 * and **10** impedances in ohms (see [Ssw532ImpedanceMap]).
 */
object Wla25 {
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
        /** Segmental fat/muscle kg+% for LA, RA, LL, RL, trunk */
        val leftArmFatKg: Double,
        val leftArmFatPct: Double,
        val leftArmMuscleKg: Double,
        val leftArmMusclePct: Double,
        val rightArmFatKg: Double,
        val rightArmFatPct: Double,
        val rightArmMuscleKg: Double,
        val rightArmMusclePct: Double,
        val leftLegFatKg: Double,
        val leftLegFatPct: Double,
        val leftLegMuscleKg: Double,
        val leftLegMusclePct: Double,
        val rightLegFatKg: Double,
        val rightLegFatPct: Double,
        val rightLegMuscleKg: Double,
        val rightLegMusclePct: Double,
        val trunkFatKg: Double,
        val trunkFatPct: Double,
        val trunkMuscleKg: Double,
        val trunkMusclePct: Double,
    )

    /** Coerce to IEEE-754 binary32 like the native library. */
    private fun f32(x: Double): Double = x.toFloat().toDouble()

    /**
     * Device rounding: 1 decimal, half-up, float32. mirrors ICAlgCommon::ceil.
     */
    fun round1(xIn: Double): Double {
        val x = xIn
        val ip = truncate(x)
        var fr = f32(x % 1.0)
        fr = f32(fr * 10.0)
        val fr2 = f32(fr % 1.0)
        var up = f32(fr + 1.0)
        if (fr2 <= 0.5) up = fr
        up = f32(truncate(up) / 10.0)
        if (up == 0.0 && (x - ip) > 0.99) up = 1.0
        return f32(up + f32(ip))
    }

    private val FFM_FACTOR = doubleArrayOf(0.77, 0.85)
    private val BFM_FACTOR = doubleArrayOf(0.23, 0.15)
    private val SCORE_CORR = doubleArrayOf(-0.958, 0.983)
    private val IMP_MIN = doubleArrayOf(1.0, 100.0, 100.0, 100.0, 100.0, 1.0, 100.0, 100.0, 100.0, 100.0)

    private fun standardBmi(age: Int, sex: Int): Double {
        if (age < 18) {
            // Under-18 height tree not ported; use adult constant (same as sacoma for adults).
            return if (sex == 1) 22.0 else 21.0
        }
        return if (sex == 1) 22.0 else 21.0
    }

    private fun stdWeight(height: Int, age: Int, sex: Int): Double {
        val bmi = f32(standardBmi(age, sex))
        val h = f32(height / 100.0)
        return f32(h * h * bmi)
    }

    private fun standardFfm(height: Int, age: Int, sex: Int): Double =
        f32(FFM_FACTOR[if (sex == 1) 1 else 0] * stdWeight(height, age, sex))

    private fun standardBfm(height: Int, age: Int, sex: Int): Double =
        f32(BFM_FACTOR[if (sex == 1) 1 else 0] * stdWeight(height, age, sex))

    private fun score(
        height: Int,
        weight: Double,
        age: Int,
        sex: Int,
        bodyFatPct: Double,
    ): Int {
        val bmi = f32(standardBmi(age, sex))
        val fatKg = f32((bodyFatPct / 100.0) * weight)
        val sw = f32(f32(height / 100.0) * f32(height / 100.0) * bmi)
        val resid = f32(fatKg - f32(BFM_FACTOR[if (sex == 1) 1 else 0] * sw))
        val corr = SCORE_CORR[if (resid < 0.0) 1 else 0]
        return ((weight - fatKg) - f32(FFM_FACTOR[if (sex == 1) 1 else 0] * sw) + 80.0 + corr * resid).toInt()
    }

    private fun metabolicAge(age: Int, bodyFatPct: Double, sex: Int): Int {
        if (age < 10) return age
        val bf = bodyFatPct
        val d = if (sex == 1) {
            when {
                bf < 14 -> -3
                bf < 19 -> -2
                bf < 24 -> -1
                bf < 27 -> 1
                bf < 30 -> 2
                bf < 33 -> 3
                bf < 36 -> 4
                else -> 5
            }
        } else {
            when {
                bf < 24 -> -3
                bf < 28 -> -2
                bf < 32 -> -1
                bf < 35 -> 1
                bf < 38 -> 2
                bf < 42 -> 3
                bf < 45 -> 4
                bf < 46 -> 0
                else -> 5
            }
        }
        return age + d
    }

    /**
     * @return null if validation gates fail
     */
    fun calc(
        weightKg: Double,
        heightCm: Int,
        sexMale1: Int,
        age: Int,
        peopleType: Int,
        imps: DoubleArray,
    ): Result? {
        require(imps.size == 10) { "WLA25 needs 10 impedances" }
        val iVar6 = heightCm
        val iVar1 = sexMale1
        val iVar2 = age
        var dVar33 = weightKg

        val dVar32 = round1(dVar33 * 10000.0 / (iVar6 * iVar6))
        val p = DoubleArray(0x42)
        p[0] = dVar32

        val dVar27 = round1(standardBfm(iVar6, iVar2, iVar1))
        val dVar40 = round1(standardFfm(iVar6, iVar2, iVar1))
        val dVar29 = iVar6.toDouble()

        if (iVar6 !in 100..220) return null
        if (dVar33 < 20.0 || dVar33 > 200.0) return null
        for (i in 0 until 10) {
            if (imps[i] < IMP_MIN[i]) return null
        }

        var dVar26 = imps[0]
        val dVar34 = imps[1]
        var dVar41 = imps[2]
        val dVar31In = imps[3]
        val dVar36In = imps[4]
        var dVar28 = imps[5]
        val dVar38In = imps[6]
        val dVar45In = imps[7]
        val dVar30In = imps[8]
        val dVar43In = imps[9]

        val dVar44 = dVar26 * 0.826
        var dVar37 = if (dVar28 <= dVar26) dVar28 * 0.826 else dVar44 - 3.0
        if (dVar44 < 0.0 || dVar37 < 0.0) return null

        var dVar39 = (
            dVar30In * 0.07 + dVar43In * 0.153 + dVar37 * 0.439 + dVar38In * 0.019 +
                dVar45In * 0.07 + dVar29 * 0.164 + dVar33 * -0.138 + dVar32 * 2.657 +
                dVar41 * -0.053 + dVar34 * -0.000491 + dVar44 * -0.03 +
                dVar36In * -0.127 + dVar31In * -0.052 + -88.052
            )
        dVar26 = (dVar39 / dVar33) * 100.0
        if (dVar26 < 3.0) {
            dVar39 = dVar33 * 0.03
            dVar26 = 3.0
        } else if (dVar26 > 60.0) {
            dVar39 = dVar33 * 0.6
            dVar26 = 60.0
        }
        dVar28 = round1(dVar39) // fat mass kg
        val fVar11 = round1(dVar26) // body fat %

        val iVar5 = metabolicAge(iVar2, fVar11, iVar1)
        var iVar6Score = score(iVar6, dVar33, iVar2, iVar1, fVar11)
        if (iVar6Score < 21) iVar6Score = 20

        var local110 = dVar38In * 0.007476 + (dVar28 * 0.081201 - dVar34 * 0.005752) + -0.662152
        var local108 = dVar45In * 0.007476 + (dVar28 * 0.081201 - dVar41 * 0.005752) + -0.662152
        var localC0 = dVar43In * 0.008645 + (dVar28 * 0.135438 - dVar36In * 0.00801) + 0.492479
        var localC8 = dVar30In * 0.008645 + (dVar28 * 0.135438 - dVar31In * 0.00801) + 0.492479

        if (0.3 < abs(local108 - local110)) {
            if (local108 <= local110) {
                val t = (dVar41 + dVar45In) / 20213.0
                local108 = (if (dVar41 <= dVar34) t else -t) + local110
            } else {
                val t = (dVar34 + dVar38In) / 20213.0
                local110 = (if (dVar34 <= dVar41) t else -t) + local108
            }
        }
        if (0.5 < abs(localC0 - localC8)) {
            if (localC0 <= localC8) {
                val t = (dVar36In + dVar43In) / 20213.0
                localC0 = (if (dVar36In <= dVar31In) t else -t) + localC8
            } else {
                val t = (dVar31In + dVar30In) / 20213.0
                localC8 = (if (dVar31In <= dVar36In) t else -t) + localC0
            }
        }

        dVar26 = dVar33 - dVar28 // lean mass
        if (local108 < 0.1) local108 = (dVar41 + dVar45In) / 20213.0 + 0.1
        dVar39 = dVar44 * 0.068621 + dVar28 * 0.552545 + dVar37 * -0.131612 + 0.322704
        if (local110 < 0.1) local110 = (dVar34 + dVar38In) / 20113.0 + 0.1
        if (dVar39 < 0.1) dVar39 = (dVar37 + dVar44) / 20203.0 + 0.1
        if (localC0 < 0.1) localC0 = (dVar36In + dVar43In) / 20213.0 + 0.1
        var localD0 = ((dVar41 * 0.002847 + dVar26 * 0.058707) - dVar45In * 0.005857) + 0.561911
        if (localC8 < 0.1) localC8 = (dVar31In + dVar30In) / 20113.0 + 0.1
        var localF0 = ((dVar34 * 0.002847 + dVar26 * 0.058707) - dVar38In * 0.005857) + 0.561911
        if (localD0 < 0.2) localD0 = (dVar41 + dVar45In) / 20213.0 + 0.2
        dVar41 = dVar44 * 0.005246 + dVar26 * 0.440922 + dVar37 * -0.010469 + -0.275461
        if (localF0 < 0.2) localF0 = (dVar34 + dVar38In) / 20113.0 + 0.2
        var localF8 = dVar43In * 0.008157 + (dVar26 * 0.176554 - dVar36In * 0.007381) + -0.688932
        if (dVar41 < 0.7) dVar41 = (dVar37 + dVar44) / 20203.0 + 0.7
        var local100 = dVar30In * 0.008157 + (dVar26 * 0.176554 - dVar31In * 0.007381) + -0.688932
        if (localF8 < 0.2) localF8 = (dVar36In + dVar43In) / 20213.0 + 0.2
        if (local100 < 0.2) local100 = (dVar31In + dVar30In) / 20113.0 + 0.2

        val dVar34Bf = fVar11
        val dVar31Diff = dVar27 - dVar28
        var iVar8v = (dVar28 * 0.502 + dVar26 * -0.029 + -0.477).toInt()
        if (iVar8v > 0x13) iVar8v = 0x14
        var dVar36Diff = dVar40 - dVar26
        if (iVar8v < 2) iVar8v = 1
        val dVar28Water = dVar26 * 0.733
        val dVar38Sub = (dVar34Bf * -0.0002 + 0.72) * dVar34Bf
        val dVar45Mus = ((dVar28Water + dVar26 * 0.2) / dVar33) * 100.0
        p[1] = round1(dVar34Bf)
        p[3] = round1(dVar38Sub)
        p[2] = round1(dVar45Mus)
        val dVar38Bone = dVar26 * 0.067
        dVar36Diff = round1(dVar36Diff)
        val dVar45Water = (dVar28Water / dVar33) * 100.0
        p[4] = iVar8v.toDouble()
        val dVar31Ceil = round1(dVar31Diff)
        if (dVar36Diff <= 0.0) dVar36Diff = 0.0
        val dVar44Ref = dVar33 * 0.02 + dVar40 * 0.102 + dVar29 * -0.045 + 3.752
        val dVar42Ref = dVar33 * 0.059 + dVar40 * 0.168 + dVar29 * -0.056 + 4.775
        val dVar43Ref = dVar27 * 0.101 + dVar29 * -0.004 + 0.331
        val dVar37Ref = dVar27 * 0.215 + dVar29 * -0.005 + 0.391
        val dVar30Prot = ((dVar26 * 0.2) / dVar33) * 100.0
        p[5] = round1(dVar38Bone)
        val dVar28Skel = ((dVar28Water * 0.834 + -2.627) / dVar33) * 100.0
        p[6] = round1(dVar45Water)
        p[7] = round1(dVar30Prot)
        p[8] = round1(dVar28Skel)

        p[0x13] = local110
        p[0x17] = local108
        p[0x1b] = dVar39
        p[0x0b] = localC8
        p[0x0f] = localC0
        p[0x15] = localF0
        p[0x19] = localD0
        p[0x11] = localF8
        p[0x0d] = local100
        p[0x1d] = dVar41
        p[0x12] = (local110 / dVar43Ref) * 100.0
        p[0x0a] = (localC8 / dVar37Ref) * 100.0
        p[0x16] = (local108 / dVar43Ref) * 100.0
        p[0x1a] = (dVar39 / (dVar29 * 0.006 + dVar27 * 0.389 + -0.683)) * 100.0
        p[0x0e] = (localC0 / dVar37Ref) * 100.0
        p[0x14] = (localF0 / dVar44Ref) * 100.0
        p[0x18] = (localD0 / dVar44Ref) * 100.0
        p[0x1c] = (dVar41 / (dVar33 * 0.166 + dVar40 * 0.485 + dVar29 * -0.16 + 13.595)) * 100.0
        p[0x0c] = (local100 / dVar42Ref) * 100.0
        p[0x10] = (localF8 / dVar42Ref) * 100.0
        p[0x1e] = iVar6Score.toDouble()

        val bmr = (dVar26 * 21.6 + 370.0).toInt()

        return Result(
            bmi = p[0],
            bodyFatPercent = p[1],
            musclePercent = p[2],
            subcutaneousFatPercent = p[3],
            visceralFat = p[4],
            boneMassKg = p[5],
            bodyWaterPercent = p[6],
            proteinPercent = p[7],
            skeletalMusclePercent = p[8],
            bmrKcal = bmr,
            metabolicAge = iVar5,
            bodyScore = p[0x1e],
            fatMassKg = dVar28,
            leanMassKg = dVar26,
            leftArmFatKg = p[0x13],
            leftArmFatPct = p[0x12],
            leftArmMuscleKg = p[0x15],
            leftArmMusclePct = p[0x14],
            rightArmFatKg = p[0x17],
            rightArmFatPct = p[0x16],
            rightArmMuscleKg = p[0x19],
            rightArmMusclePct = p[0x18],
            leftLegFatKg = p[0x0b],
            leftLegFatPct = p[0x0a],
            leftLegMuscleKg = p[0x0d],
            leftLegMusclePct = p[0x0c],
            rightLegFatKg = p[0x0f],
            rightLegFatPct = p[0x0e],
            rightLegMuscleKg = p[0x11],
            rightLegMusclePct = p[0x10],
            trunkFatKg = p[0x1b],
            trunkFatPct = p[0x1a],
            trunkMuscleKg = p[0x1d],
            trunkMusclePct = p[0x1c],
        )
    }
}

/**
 * Map SSW532 pkt0 channels A/B + pkt1 Z1…Z8 → WLA25's 10-slot vector.
 *
 * WLA25 gates: slots 0 and 5 may be small Ω (trunk); other slots ≥ 100 Ω.
 * On FG2211WB, Z3 and Z8 are the sub-100 values.
 *
 * Channel order calibrated against Dr Trust 360 (same weigh-in, height 175 cm, age 26 male):
 * official fat 14.6% / water 62.7% / muscle 79.8% / bone 4.1 / BMR 1680 / score 83
 * matched by `[Z3, Z1, Z2, Z4, Z5, Z8, B, A, Z6, Z7]` (err ≈ 0.1).
 * Putting A/B in the last two slots (Fitdays-style) overstated fat by ~13 pp.
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
            channelBOhm, // pkt0 B. calibrated slot 6
            channelAOhm, // pkt0 A. calibrated slot 7
            z[5], // Z6 cross-body
            z[6], // Z7 cross-body
        )
    }
}
