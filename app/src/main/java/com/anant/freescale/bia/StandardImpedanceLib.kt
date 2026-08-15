package com.anant.freescale.bia

import com.anant.freescale.data.GenderType

data class StandardImpedanceLib(
    val gender: GenderType,
    val age: Int,
    val weightKg: Double,
    val heightM: Double,
    val impedance: Double,
) {
    val isMale = gender == GenderType.MALE
    val genderInt = if (isMale) 1 else 0
    val heightCm = heightM * 100.0
    val h2rCoeff = heightCm * heightCm / impedance
    val bmi: Double = weightKg / (heightM * heightM)

    val fatFreeMassKg: Double by lazy {
        if (isMale) {
            -10.68 + 0.65 * h2rCoeff + 0.26 * weightKg + 0.02 * impedance
        } else {
            -9.53 + 0.69 * h2rCoeff + 0.17 * weightKg + 0.02 * impedance
        }
    }

    val totalFatPercentage: Double = (1.0 - fatFreeMassKg / weightKg) * 100.0

    val totalBodyWaterKg: Double by lazy {
        val liters =
            if (isMale) 1.2 + 0.45 * h2rCoeff + 0.18 * weightKg
            else 3.75 + 0.45 * h2rCoeff + 0.11 * weightKg
        0.99513 * liters
    }

    val totalBodyWaterPercentage: Double by lazy {
        (totalBodyWaterKg / weightKg) * 100.0
    }

    val basalMetabolicRate: Double = fatFreeMassKg * 21.6 + 370

    val skeletalMuscleMassKg: Double by lazy {
        0.401 * h2rCoeff + 3.825 * genderInt - 0.071 * age + 5.102
    }

    val skeletalMusclePercentage: Double by lazy {
        (skeletalMuscleMassKg / weightKg) * 100.0
    }

    val boneMassKg: Double by lazy {
        val factor = if (isMale) 0.057 else 0.05
        factor * fatFreeMassKg
    }
}
