package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.SegmentMetrics
import com.anant.freescale.util.BleLogger

/**
 * Builds measurement metrics using Chipsea/ICOMON **WLA25** (best match for SSW532 hardware).
 * Falls back to Sun/Janssen literature equations only if WLA25 validation gates reject the sample.
 */
object BodyCompositionBuilder {
    fun build(
        weightKg: Float,
        heightCm: Float,
        age: Int,
        gender: GenderType,
        wholeBodyOhm: Double,
        trunkOhm: Double,
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
        val imps = Ssw532ImpedanceMap.toWla25(channelAOhm, channelBOhm, segmentsOhm)
        val wla = if (imps != null) {
            Wla25.calc(
                weightKg = weightKg.toDouble(),
                heightCm = heightCm.toInt(),
                sexMale1 = sex,
                age = age,
                peopleType = 0,
                imps = imps,
            )
        } else null

        if (wla != null && imps != null) {
            BleLogger.i(
                "WLA25 OK fat=${wla.bodyFatPercent} water=${wla.bodyWaterPercent} " +
                    "muscle=${wla.musclePercent} bone=${wla.boneMassKg} vf=${wla.visceralFat}"
            )
            return fromWla25(
                wla = wla,
                weightKg = weightKg,
                heightCm = heightCm,
                age = age,
                gender = gender,
                wholeBodyOhm = wholeBodyOhm,
                imps = imps,
                segmentsOhm = segmentsOhm,
                channelAOhm = channelAOhm,
                channelBOhm = channelBOhm,
                pkt0Cmd = pkt0Cmd,
                pkt0ValidFlag = pkt0ValidFlag,
                pkt0Hex = pkt0Hex,
                pkt1Hex = pkt1Hex,
                pkt2Hex = pkt2Hex,
            )
        }

        BleLogger.w("WLA25 rejected; falling back to Sun/Janssen literature formulas")
        return fromSunFallback(
            weightKg, heightCm, age, gender, wholeBodyOhm, trunkOhm,
            segmentsOhm, channelAOhm, channelBOhm, pkt0Cmd, pkt0ValidFlag,
            pkt0Hex, pkt1Hex, pkt2Hex,
        )
    }

    private fun fromWla25(
        wla: Wla25.Result,
        weightKg: Float,
        heightCm: Float,
        age: Int,
        gender: GenderType,
        wholeBodyOhm: Double,
        imps: DoubleArray,
        segmentsOhm: List<Double>,
        channelAOhm: Double,
        channelBOhm: Double,
        pkt0Cmd: Int,
        pkt0ValidFlag: Int,
        pkt0Hex: String,
        pkt1Hex: String,
        pkt2Hex: String,
    ): ScaleMeasurement {
        val fatMass = wla.fatMassKg.toFloat()
        val waterPct = wla.bodyWaterPercent.toFloat()
        val proteinPct = wla.proteinPercent.toFloat()
        val ideal = idealWeightKg(heightCm, gender)
        val hCm = heightCm.toDouble()
        return ScaleMeasurement(
            dateTime = java.util.Date(),
            genderLabel = if (gender == GenderType.MALE) "Male" else "Female",
            algorithm = "Chipsea/ICOMON WLA25 (sacoma-lib port)",
            weight = weightKg,
            heightCm = heightCm,
            ageYears = age,
            bmi = wla.bmi.toFloat(),
            fat = wla.bodyFatPercent.toFloat(),
            fatMassKg = fatMass,
            subcutaneousFat = wla.subcutaneousFatPercent.toFloat(),
            visceralFat = wla.visceralFat.toFloat(),
            water = waterPct,
            waterKg = weightKg * waterPct / 100f,
            muscle = wla.musclePercent.toFloat(),
            muscleMassKg = weightKg * wla.musclePercent.toFloat() / 100f,
            skeletalMuscle = wla.skeletalMusclePercent.toFloat(),
            bone = wla.boneMassKg.toFloat(),
            protein = proteinPct,
            proteinKg = weightKg * proteinPct / 100f,
            lbm = wla.leanMassKg.toFloat(),
            bmr = wla.bmrKcal.toFloat(),
            bodyAge = wla.metabolicAge,
            obesityDegree = ((weightKg / ideal) - 1f) * 100f,
            idealWeightKg = ideal,
            bodyScore = wla.bodyScore.toFloat(),
            impedance = wholeBodyOhm,
            h2rCoeff = hCm * hCm / wholeBodyOhm,
            channelAOhm = channelAOhm,
            channelBOhm = channelBOhm,
            pkt0Cmd = pkt0Cmd,
            pkt0ValidFlag = pkt0ValidFlag,
            zSegments = segmentsOhm.map { it.toFloat() },
            wla25Inputs = imps.map { it.toFloat() },
            segments = listOf(
                SegmentMetrics("Left arm", wla.leftArmFatKg.toFloat(), wla.leftArmFatPct.toFloat(), wla.leftArmMuscleKg.toFloat(), wla.leftArmMusclePct.toFloat()),
                SegmentMetrics("Right arm", wla.rightArmFatKg.toFloat(), wla.rightArmFatPct.toFloat(), wla.rightArmMuscleKg.toFloat(), wla.rightArmMusclePct.toFloat()),
                SegmentMetrics("Trunk", wla.trunkFatKg.toFloat(), wla.trunkFatPct.toFloat(), wla.trunkMuscleKg.toFloat(), wla.trunkMusclePct.toFloat()),
                SegmentMetrics("Left leg", wla.leftLegFatKg.toFloat(), wla.leftLegFatPct.toFloat(), wla.leftLegMuscleKg.toFloat(), wla.leftLegMusclePct.toFloat()),
                SegmentMetrics("Right leg", wla.rightLegFatKg.toFloat(), wla.rightLegFatPct.toFloat(), wla.rightLegMuscleKg.toFloat(), wla.rightLegMusclePct.toFloat()),
            ),
            pkt0Hex = pkt0Hex,
            pkt1Hex = pkt1Hex,
            pkt2Hex = pkt2Hex,
        )
    }

    private fun fromSunFallback(
        weightKg: Float,
        heightCm: Float,
        age: Int,
        gender: GenderType,
        wholeBodyOhm: Double,
        trunkOhm: Double,
        segmentsOhm: List<Double>,
        channelAOhm: Double,
        channelBOhm: Double,
        pkt0Cmd: Int,
        pkt0ValidFlag: Int,
        pkt0Hex: String,
        pkt1Hex: String,
        pkt2Hex: String,
    ): ScaleMeasurement {
        val lib = StandardImpedanceLib(
            gender = gender,
            age = age,
            weightKg = weightKg.toDouble(),
            heightM = heightCm / 100.0,
            impedance = wholeBodyOhm,
        )
        val fatPct = lib.totalFatPercentage.toFloat().coerceIn(0f, 75f)
        val waterPct = lib.totalBodyWaterPercentage.toFloat().coerceIn(0f, 80f)
        val musclePct = lib.skeletalMusclePercentage.toFloat().coerceIn(0f, 99f)
        val boneKg = lib.boneMassKg.toFloat()
        val lbmKg = lib.fatFreeMassKg.toFloat()
        val fatMass = weightKg * fatPct / 100f
        val waterKg = weightKg * waterPct / 100f
        val genderInt = if (gender == GenderType.MALE) 1 else 0
        val visceral = estimateVisceralFat(weightKg, trunkOhm, age, genderInt)
        val ideal = idealWeightKg(heightCm, gender)
        val proteinKg = (lbmKg - boneKg - waterKg * 0.73f).coerceAtLeast(0f)
        return ScaleMeasurement(
            dateTime = java.util.Date(),
            genderLabel = if (gender == GenderType.MALE) "Male" else "Female",
            algorithm = "Fallback: Sun 2003 FFM/TBW + Janssen SMM (literature)",
            weight = weightKg,
            heightCm = heightCm,
            ageYears = age,
            bmi = lib.bmi.toFloat(),
            fat = fatPct,
            fatMassKg = fatMass,
            subcutaneousFat = (fatPct - visceral * 0.6f).coerceIn(1f, fatPct),
            visceralFat = visceral,
            water = waterPct,
            waterKg = waterKg,
            muscle = musclePct,
            muscleMassKg = lib.skeletalMuscleMassKg.toFloat(),
            skeletalMuscle = musclePct,
            bone = boneKg,
            protein = (proteinKg / weightKg * 100f).coerceIn(5f, 30f),
            proteinKg = proteinKg,
            lbm = lbmKg,
            bmr = lib.basalMetabolicRate.toFloat(),
            bodyAge = age,
            obesityDegree = ((weightKg / ideal) - 1f) * 100f,
            idealWeightKg = ideal,
            bodyScore = 0f,
            impedance = wholeBodyOhm,
            h2rCoeff = lib.h2rCoeff,
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

    private fun idealWeightKg(heightCm: Float, gender: GenderType): Float {
        val base = if (gender == GenderType.MALE) heightCm - 100f else heightCm - 105f
        return base.coerceIn(40f, 120f)
    }

    private fun estimateVisceralFat(weightKg: Float, trunkZ: Double, age: Int, gender: Int): Float {
        if (trunkZ <= 0.0) return 1f
        val tlr = weightKg / trunkZ
        val genderOffset = if (gender == 1) 0.0f else -1.0f
        return (2.87f * tlr.toFloat() + 0.01f * age + genderOffset).coerceIn(1f, 25f)
    }
}
