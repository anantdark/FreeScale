package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.SegmentMetrics
import com.anant.freescale.util.BleLogger

/**
 * Turns one SSW532 weigh-in into a [ScaleMeasurement].
 *
 *  1. Body fat comes from the official **WLA37** native lib ([Wla37]), fed the 10 impedances
 *     decoded by [Ssw532Impedances]: the same vector Dr. Trust 360 passes it.
 *  2. [Wla25.derive] fills the remaining metrics from that body fat.
 *
 * If the impedances or the native lib are unavailable (e.g. a non-arm64 device) the result
 * is weight only. There is deliberately no estimated fallback: a guessed body fat that
 * looks plausible is worse than none.
 */
object BodyCompositionBuilder {
    fun build(
        weightKg: Float,
        heightCm: Float,
        age: Int,
        gender: GenderType,
        /** The 10 WLA37 impedances from [Ssw532Impedances.decode], ohm; null if BIA failed. */
        impedances: DoubleArray?,
        pkt0Cmd: Int = 0,
        pkt0Hex: String = "",
        pkt1Hex: String = "",
        pkt2Hex: String = "",
    ): ScaleMeasurement {
        val sex = if (gender == GenderType.MALE) 1 else 0
        val imps = impedances?.takeIf { it.size == Ssw532Impedances.COUNT }

        val fat = imps?.let {
            Wla37.calcBodyFatPercent(
                weightKg = weightKg.toDouble(),
                heightCm = heightCm.toInt(),
                age = age,
                sexMale = gender == GenderType.MALE,
                imps = it,
            )
        }
        if (imps == null || fat == null) {
            BleLogger.w("No body fat (impedances=${imps != null}, wla37=${fat != null}); weight only")
            return ScaleMeasurement(
                dateTime = java.util.Date(),
                genderLabel = genderLabel(gender),
                algorithm = "Weight only (body fat unavailable)",
                weight = weightKg,
                heightCm = heightCm,
                ageYears = age,
                impedances = imps?.map { it.toFloat() } ?: emptyList(),
                pkt0Cmd = pkt0Cmd,
                pkt0Hex = pkt0Hex,
                pkt1Hex = pkt1Hex,
                pkt2Hex = pkt2Hex,
            )
        }
        val algorithmLabel = "ICOMON WLA37 (official) + WLA25 derivation"
        val wholeBodyOhm = imps[1]

        val wla = Wla25.derive(
            heightCm = heightCm.toInt(),
            rawWeightKg = weightKg.toDouble(),
            age = age,
            sexMale1 = sex,
            bodyFatPercentRaw = fat,
            imps = imps,
        )

        BleLogger.i(
            "BIA $algorithmLabel: fat=${wla.bodyFatPercent}% water=${wla.bodyWaterPercent}% " +
                "muscle=${wla.musclePercent}% bone=${wla.boneMassKg} vf=${wla.visceralFat} " +
                "bmr=${wla.bmrKcal} score=${wla.bodyScore}"
        )

        val ideal = wla.standardWeightKg.toFloat()
        return ScaleMeasurement(
            dateTime = java.util.Date(),
            genderLabel = genderLabel(gender),
            algorithm = algorithmLabel,
            weight = weightKg,
            heightCm = heightCm,
            ageYears = age,
            bmi = wla.bmi.toFloat(),
            fat = wla.bodyFatPercent.toFloat(),
            fatMassKg = wla.fatMassKg.toFloat(),
            subcutaneousFat = wla.subcutaneousFatPercent.toFloat(),
            visceralFat = wla.visceralFat.toFloat(),
            water = wla.bodyWaterPercent.toFloat(),
            waterKg = weightKg * wla.bodyWaterPercent.toFloat() / 100f,
            muscle = wla.musclePercent.toFloat(),
            muscleMassKg = weightKg * wla.musclePercent.toFloat() / 100f,
            skeletalMuscle = wla.skeletalMusclePercent.toFloat(),
            bone = wla.boneMassKg.toFloat(),
            protein = wla.proteinPercent.toFloat(),
            proteinKg = weightKg * wla.proteinPercent.toFloat() / 100f,
            lbm = wla.leanMassKg.toFloat(),
            bmr = wla.bmrKcal.toFloat(),
            bodyAge = wla.metabolicAge,
            obesityDegree = ((weightKg / ideal) - 1f) * 100f,
            idealWeightKg = ideal,
            bodyScore = wla.bodyScore.toFloat(),
            impedance = wholeBodyOhm,
            h2rCoeff = heightCm.toDouble() * heightCm.toDouble() / wholeBodyOhm.coerceAtLeast(1.0),
            pkt0Cmd = pkt0Cmd,
            impedances = imps.map { it.toFloat() },
            segments = wla.segments.map {
                SegmentMetrics(
                    name = it.name,
                    fatKg = it.fatKg.toFloat(),
                    fatPct = it.fatPct.toFloat(),
                    muscleKg = it.muscleKg.toFloat(),
                    musclePct = it.musclePct.toFloat(),
                )
            },
            pkt0Hex = pkt0Hex,
            pkt1Hex = pkt1Hex,
            pkt2Hex = pkt2Hex,
        )
    }

    private fun genderLabel(gender: GenderType) =
        if (gender == GenderType.MALE) "Male" else "Female"
}
