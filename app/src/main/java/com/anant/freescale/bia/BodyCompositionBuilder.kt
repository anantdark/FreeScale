package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.SegmentMetrics
import com.anant.freescale.util.BleLogger

/**
 * Turns one SSW532 weigh-in into a [ScaleMeasurement].
 *
 * Two stages:
 *
 *  1. [Ssw532FatModel] estimates body fat from the scale's whole-body impedance
 *     using the Sun 2003 equation in [StandardImpedanceLib].
 *  2. [Wla25.derive] takes it from there. Every other metric follows from
 *     fat-free mass, using the vendor's own definitions, which is why the
 *     output matches the Dr. Trust app exactly.
 *
 * The vendor's own body-fat regression is deliberately not used; see
 * [Wla25.fatMassFromImpedances].
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

        // Only feeds the segmental breakdown; see Ssw532ImpedanceMap.
        val imps = Ssw532ImpedanceMap.toWla25(channelAOhm, channelBOhm, segmentsOhm)

        val wla = Wla25.derive(
            heightCm = heightCm.toInt(),
            rawWeightKg = weightKg.toDouble(),
            age = age,
            sexMale1 = sex,
            bodyFatPercentRaw = estimate.bodyFatPercent,
            imps = imps,
        )

        BleLogger.i(
            "BIA ${estimate.source.label}: R=${"%.1f".format(estimate.wholeBodyOhm)}Ω " +
                "fat=${wla.bodyFatPercent}% water=${wla.bodyWaterPercent}% " +
                "muscle=${wla.musclePercent}% bone=${wla.boneMassKg} vf=${wla.visceralFat} " +
                "bmr=${wla.bmrKcal} score=${wla.bodyScore}"
        )

        // Every kg figure is derived from the exact weight the scale reported, not a
        // one-decimal rounding of it, so a 71.15 kg weigh-in stays 71.15 throughout.
        val ideal = wla.standardWeightKg.toFloat()
        return ScaleMeasurement(
            dateTime = java.util.Date(),
            genderLabel = genderLabel(gender),
            algorithm = "Sun 2003 body fat (${estimate.source.label}) + Chipsea/ICOMON WLA25 derivation",
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
            impedance = estimate.wholeBodyOhm,
            h2rCoeff = heightCm.toDouble() * heightCm.toDouble() / estimate.wholeBodyOhm,
            channelAOhm = channelAOhm,
            channelBOhm = channelBOhm,
            pkt0Cmd = pkt0Cmd,
            pkt0ValidFlag = pkt0ValidFlag,
            zSegments = segmentsOhm.map { it.toFloat() },
            wla25Inputs = imps?.map { it.toFloat() } ?: emptyList(),
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
