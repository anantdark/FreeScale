package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.SegmentMetrics
import com.anant.freescale.util.BleLogger

/**
 * Turns one SSW532 weigh-in into a [ScaleMeasurement].
 *
 *  1. Prefer official **WLA37** body fat via [Wla37] + [Ssw532ImpedanceMap.toWla37]
 *     (same `libICBodyFatAlgorithms.so` path as Dr. Trust 360).
 *  2. Fall back to [Ssw532FatModel] (Sun 2003) if native calc is unavailable.
 *  3. [Wla25.derive] fills the remaining metrics from fat-free mass.
 */
object BodyCompositionBuilder {
    fun build(
        weightKg: Float,
        heightCm: Float,
        age: Int,
        gender: GenderType,
        /** Foot-to-foot path Z3+Z4+Z5, ohm. Fallback input when channel A is unusable. */
        wholeBodyOhm: Double,
        segmentsOhm: List<Double>,
        channelAOhm: Double = 0.0,
        channelBOhm: Double = 0.0,
        pkt0Cmd: Int = 0,
        pkt0ValidFlag: Int = 0,
        pkt0Hex: String = "",
        pkt1Hex: String = "",
        pkt2Hex: String = "",
    ): ScaleMeasurement {
        val sex = if (gender == GenderType.MALE) 1 else 0
        val imps = Ssw532ImpedanceMap.toWla37(channelAOhm, channelBOhm, segmentsOhm)

        var bodyFatPercent: Double? = null
        var algorithmLabel = ""
        var impedanceForMeta = wholeBodyOhm

        if (imps != null) {
            bodyFatPercent = Wla37.calcBodyFatPercent(
                weightKg = weightKg.toDouble(),
                heightCm = heightCm.toInt(),
                age = age,
                sexMale = gender == GenderType.MALE,
                imps = imps,
            )
            if (bodyFatPercent != null) {
                algorithmLabel = "ICOMON WLA37 (official) + WLA25 derivation"
                impedanceForMeta = channelAOhm
            }
        }

        if (bodyFatPercent == null) {
            val estimate = Ssw532FatModel.estimate(
                weightKg = weightKg.toDouble(),
                heightCm = heightCm.toDouble(),
                age = age,
                gender = gender,
                channelAOhm = channelAOhm,
                z6Ohm = segmentsOhm.getOrElse(5) { 0.0 },
                footPathOhm = wholeBodyOhm,
            )
            if (estimate == null) {
                BleLogger.w(
                    "No usable impedance path (chA=$channelAOhm footPath=$wholeBodyOhm); weight only"
                )
                return ScaleMeasurement(
                    dateTime = java.util.Date(),
                    genderLabel = genderLabel(gender),
                    algorithm = "Weight only (impedance unusable)",
                    weight = weightKg,
                    heightCm = heightCm,
                    ageYears = age,
                    impedance = wholeBodyOhm,
                    channelAOhm = channelAOhm,
                    channelBOhm = channelBOhm,
                    pkt0Cmd = pkt0Cmd,
                    pkt0ValidFlag = pkt0ValidFlag,
                    zSegments = segmentsOhm.map { it.toFloat() },
                    pkt0Hex = pkt0Hex,
                    pkt1Hex = pkt1Hex,
                    pkt2Hex = pkt2Hex,
                )
            }
            bodyFatPercent = estimate.bodyFatPercent
            algorithmLabel =
                "Sun 2003 body fat (${estimate.source.label}) + Chipsea/ICOMON WLA25 derivation"
            impedanceForMeta = estimate.wholeBodyOhm
            BleLogger.w("WLA37 unavailable; fell back to ${estimate.source.label}")
        }

        val fat = bodyFatPercent!!
        val segmentalImps = imps ?: Ssw532ImpedanceMap.toWla25(channelAOhm, channelBOhm, segmentsOhm)

        val wla = Wla25.derive(
            heightCm = heightCm.toInt(),
            rawWeightKg = weightKg.toDouble(),
            age = age,
            sexMale1 = sex,
            bodyFatPercentRaw = fat,
            imps = segmentalImps,
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
            impedance = impedanceForMeta,
            h2rCoeff = heightCm.toDouble() * heightCm.toDouble() / impedanceForMeta.coerceAtLeast(1.0),
            channelAOhm = channelAOhm,
            channelBOhm = channelBOhm,
            pkt0Cmd = pkt0Cmd,
            pkt0ValidFlag = pkt0ValidFlag,
            zSegments = segmentsOhm.map { it.toFloat() },
            wla25Inputs = segmentalImps?.map { it.toFloat() } ?: emptyList(),
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
