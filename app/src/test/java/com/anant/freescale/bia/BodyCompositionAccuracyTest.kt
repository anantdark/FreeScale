package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the body composition derivation against the Dr. Trust official app.
 *
 * Given a body-fat percentage, [Wla25] reproduces all 18 metrics the vendor app
 * displays, to the last digit. Body fat itself comes from the native WLA37 lib,
 * which only runs on an arm64 device; see the instrumented `Wla37OfficialVectorTest`.
 */
class BodyCompositionAccuracyTest {

    private val age = 26

    // ------------------------------------------------------------------
    // The derivation chain: exact
    // ------------------------------------------------------------------

    /**
     * Fed the vendor app's own body fat, the chain must reproduce every other
     * number it displayed for that weigh-in. This is the claim that justifies
     * keeping the vendor's derivation and replacing only its fat regression.
     */
    @Test
    fun `chain reproduces all 18 official metrics from the official body fat`() {
        val w = Wla25.derive(
            heightCm = 175,
            rawWeightKg = 71.20,
            age = age,
            sexMale1 = 1,
            bodyFatPercentRaw = 14.6,
        )

        assertEquals("body_weight", 71.20, 71.20, 0.005)
        assertEquals("bmi", 23.20, w.bmi, 0.005)
        assertEquals("body_fat", 14.60, w.bodyFatPercent, 0.005)
        assertEquals("muscle_rate", 79.70, w.musclePercent, 0.005)
        assertEquals("body_water", 62.60, w.bodyWaterPercent, 0.005)
        assertEquals("bone_mass", 4.10, w.boneMassKg, 0.005)
        assertEquals("bmr", 1683, w.bmrKcal)
        assertEquals("metabolic_age", 24, w.metabolicAge)
        assertEquals("viceral_fat", 2.0, w.visceralFat, 0.005)
        assertEquals("subcutaneous_fat", 10.50, w.subcutaneousFatPercent, 0.005)
        assertEquals("protein_mass", 17.10, w.proteinPercent, 0.005)
        assertEquals("weight_without_fat", 60.80, w.leanMassKg, 0.005)
        assertEquals("skeletal_muscle_mass", 48.50, w.skeletalMusclePercent, 0.005)
        assertEquals("fat_mass", 10.40, w.fatMassKg, 0.005)
        assertEquals("body_score", 83.0, w.bodyScore, 0.5)
        assertEquals("ideal_body_weight", 67.40, w.standardWeightKg, 0.03)

        // The kg figures the builder derives, now from the exact weight.
        assertEquals("muscle_mass", 56.75, 71.20 * w.musclePercent / 100.0, 0.005)
        assertEquals("water_weight", 44.57, 71.20 * w.bodyWaterPercent / 100.0, 0.005)
    }

    /**
     * Ideal weight is the vendor's standard weight (reference BMI times height
     * squared), not Broca. Broca gives 75 kg at 175 cm and did not match.
     */
    @Test
    fun `ideal weight is standard weight, not Broca`() {
        assertEquals(67.375, Wla25.standardWeightKg(175.0, 1), 0.01)
        assertEquals(64.3125, Wla25.standardWeightKg(175.0, 0), 0.01)
    }

    /**
     * Results carry float32 precision on purpose, so compare at float32 width:
     * `round1(26.35)` is the double holding 26.4f, i.e. 26.399999618530273.
     */
    @Test
    fun `round1 is half-up in single precision`() {
        assertEquals(26.4f, Wla25.round1(26.35).toFloat(), 0f)
        // 1.95 has a float32 fraction of exactly 0.95, so the half-up test fails.
        assertEquals(1.9f, Wla25.round1(1.95).toFloat(), 0f)
        assertEquals(10.4f, Wla25.round1(10.3952).toFloat(), 0f)
    }

    @Test
    fun `metabolic age bands`() {
        assertEquals(24, Wla25.bodyAge(26, 14.6, 1))   // male, lean -> -2
        assertEquals(23, Wla25.bodyAge(26, 13.9, 1))   // male, < 14 -> -3
        assertEquals(31, Wla25.bodyAge(26, 40.0, 1))   // male, very high -> +5
        assertEquals(23, Wla25.bodyAge(26, 23.0, 0))   // female, < 24 -> -3
        assertEquals(26, Wla25.bodyAge(26, 45.5, 0))   // female [45,46) -> +0
    }

    /** Fat-free mass must come off the exact weight. */
    @Test
    fun `lean mass uses the exact weight`() {
        val w = Wla25.derive(175, 71.15, 26, 1, 15.4)
        assertEquals(71.15 - w.fatMassKg, w.leanMassKg, 1e-9)
    }

    /** Without usable impedances there is no body fat, only the weight. */
    @Test
    fun `publishes weight only when no impedance is usable`() {
        val m = BodyCompositionBuilder.build(
            weightKg = 71.2f,
            heightCm = 175f,
            age = 26,
            gender = GenderType.MALE,
            impedances = null,
        )
        assertEquals(71.2f, m.weight, 0.001f)
        assertEquals(0f, m.fat, 0.001f)
        assertTrue(!m.hasBodyComp)
    }
}
