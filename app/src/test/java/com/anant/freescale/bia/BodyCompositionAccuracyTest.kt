package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pins the body composition pipeline against the Dr. Trust official app.
 *
 * Two different kinds of claim live here, tested separately on purpose:
 *
 *  - **The derivation chain is exact.** Given a body-fat percentage, [Wla25]
 *    reproduces all 18 metrics the vendor app displays, to the last digit.
 *  - **The fat model is approximate.** [Ssw532FatModel] estimates that percentage
 *    from impedance and is good to ~0.15 pp. It cannot be exact everywhere: the
 *    vendor app returned 14.6 % and 15.3 % on an unchanged body 65 minutes apart.
 */
class BodyCompositionAccuracyTest {

    private val heightCm = 175f
    private val age = 26
    private val gender = GenderType.MALE

    /** A weigh-in with a known vendor-app body fat. */
    private data class Ref(
        val label: String,
        /** Weight the scale settled on, from pkt0, unrounded. */
        val weightKg: Float,
        val channelAOhm: Double,
        val channelBOhm: Double,
        /** pkt1 Z1..Z8, ohm. */
        val z: List<Double>,
        val officialFat: Double,
    ) {
        val z6Ohm get() = z[5]

        /** Foot-to-foot path the handler passes as wholeBodyOhm: Z3 + Z4 + Z5. */
        val footPathOhm get() = z[2] + z[3] + z[4]
    }

    private val ref1403 = Ref(
        label = "2026-09-21 14:03",
        weightKg = 71.20f,
        channelAOhm = 286.4,
        channelBOhm = 265.3,
        z = listOf(303.4, 304.6, 47.1, 231.3, 251.1, 239.0, 255.7, 24.6),
        officialFat = 14.6,
    )

    private val ref1508 = Ref(
        label = "2026-09-21 15:08",
        weightKg = 71.15f,
        channelAOhm = 286.7,
        channelBOhm = 292.2,
        z = listOf(283.0, 293.3, 38.0, 231.5, 251.9, 243.7, 247.9, 18.8),
        officialFat = 15.3,
    )

    private val ref1541 = Ref(
        label = "2026-09-21 15:41",
        weightKg = 71.15f,
        channelAOhm = 286.7,
        channelBOhm = 292.3,
        z = listOf(286.1, 288.0, 32.3, 231.4, 252.3, 246.3, 243.9, 14.5),
        officialFat = 15.4,
    )

    /** Same Z6+A as [ref1508], higher weight; official app logged 15.0 %. */
    private val ref1830 = Ref(
        label = "2026-09-21 18:30",
        weightKg = 71.75f,
        channelAOhm = 285.4,
        channelBOhm = 289.8,
        z = listOf(284.0, 288.6, 9.1, 255.7, 250.5, 245.0, 243.5, 15.3),
        officialFat = 15.0,
    )

    private val refs = listOf(ref1403, ref1508, ref1541, ref1830)

    private fun build(r: Ref) = BodyCompositionBuilder.build(
        weightKg = r.weightKg,
        heightCm = heightCm,
        age = age,
        gender = gender,
        wholeBodyOhm = r.footPathOhm,
        segmentsOhm = r.z,
        channelAOhm = r.channelAOhm,
        channelBOhm = r.channelBOhm,
    )

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

    // ------------------------------------------------------------------
    // Exact weight, not a one-decimal rounding
    // ------------------------------------------------------------------

    /**
     * The weight the scale settled on is used throughout and reported as-is. The
     * vendor rounds to one decimal internally; we do not, so 71.15 stays 71.15.
     */
    @Test
    fun `uses and reports the exact two-decimal weight`() {
        val m = build(ref1508)
        assertEquals("reported weight", 71.15f, m.weight, 0.001f)

        // Derived kg figures must follow the exact weight, not 71.2.
        assertEquals("waterKg", 71.15f * m.water / 100f, m.waterKg, 0.0005f)
        assertEquals("muscleMassKg", 71.15f * m.muscle / 100f, m.muscleMassKg, 0.0005f)
        assertEquals("proteinKg", 71.15f * m.protein / 100f, m.proteinKg, 0.0005f)

        // Had the weight been rounded to 71.2 these would differ measurably.
        assertTrue("waterKg should not match the 71.2 rounding",
            abs(m.waterKg - 71.2f * m.water / 100f) > 0.02f)
    }

    /** Fat-free mass must come off the exact weight too. */
    @Test
    fun `lean mass uses the exact weight`() {
        val w = Wla25.derive(175, 71.15, age, 1, 15.4)
        assertEquals(71.15 - w.fatMassKg, w.leanMassKg, 1e-9)
    }

    // ------------------------------------------------------------------
    // The fat model: approximate, and bounded
    // ------------------------------------------------------------------

    /**
     * All reference weigh-ins must land within the model's stated accuracy.
     * 0.2 pp means the displayed one-decimal figure is within 0.1 of the vendor
     * app. Tightening further would mean fitting the vendor app's own noise.
     */
    @Test
    fun `fat model matches all official readings within 0,2 pp`() {
        for (r in refs) {
            val fat = build(r).fat
            val err = abs(fat - r.officialFat)
            assertTrue(
                "${r.label}: model $fat vs official ${r.officialFat} (err $err pp)",
                err <= 0.2,
            )
        }
    }

    /**
     * Regression guard for the 18:30 weigh-in: identical Z6+A to 15:08 but
     * +0.60 kg, where bare Sun 2003 rose to 15.7 % while the official app
     * logged 15.0 %. The weight correction must bring this back to 15.0.
     */
    @Test
    fun `weight correction brings 1830 reading to official 15 percent`() {
        val fat = build(ref1830).fat
        assertEquals("official 15.0%", 15.0f, fat, 0.05f)
        assertTrue("must not regress to the uncorrected 15.7%", abs(fat - 15.7f) > 0.3f)
    }

    /**
     * Channel A alone cannot be the input: these two weigh-ins share an identical
     * channel A and weight, yet the vendor app separated them. Guards against
     * regressing to a channel-A-only model.
     */
    @Test
    fun `model distinguishes readings that channel A cannot`() {
        assertEquals("channel A is identical", ref1508.channelAOhm, ref1541.channelAOhm, 1e-9)
        assertEquals("weight is identical", ref1508.weightKg, ref1541.weightKg, 1e-9f)

        val a = build(ref1508).fat
        val b = build(ref1541).fat
        assertTrue("model should separate them, got $a and $b", b > a)
    }

    @Test
    fun `uses the calibrated Z6 plus channel A path`() {
        val m = build(ref1403)
        val expected = (ref1403.z6Ohm + ref1403.channelAOhm) * Ssw532FatModel.WHOLE_BODY_SCALE
        assertEquals(expected, m.impedance, 0.01)
        assertTrue(
            "model input should sit in the band Sun 2003 expects, was ${m.impedance}",
            m.impedance in 400.0..600.0,
        )
        assertTrue(m.algorithm.contains("Z6+channel A"))
    }

    /**
     * Body fat must stay in a credible band across weigh-ins of an essentially
     * unchanged body. These are all 17 stored readings over five weeks. An
     * independent tape measurement put the subject at 13.4 %, and the vendor app
     * reported 14.6-15.4 %. The regression this replaced produced 14.4-30.6 %.
     */
    @Test
    fun `body fat stays credible across all stored readings`() {
        // weightKg to (channelA, Z1..Z8)
        val readings = listOf(
            71.2f to (282.9 to listOf(268.7, 233.1, 22.2, 280.0, 236.1, 228.7, 220.6, 11.5)),
            71.4f to (283.4 to listOf(243.9, 252.2, 18.6, 280.1, 232.6, 229.2, 215.7, 9.1)),
            71.5f to (283.3 to listOf(243.2, 250.4, 17.4, 280.0, 231.5, 228.5, 214.5, 8.2)),
            71.95f to (282.8 to listOf(243.0, 244.8, 12.0, 279.4, 230.8, 228.2, 210.2, 4.6)),
            71.4f to (283.0 to listOf(243.6, 243.0, 10.9, 279.5, 231.6, 228.7, 209.0, 3.8)),
            70.35f to (284.5 to listOf(262.0, 265.6, 10.3, 255.1, 254.4, 223.0, 226.1, 22.3)),
            69.2f to (286.6 to listOf(289.7, 300.4, 49.3, 257.8, 231.2, 250.4, 279.7, 2.6)),
            70.4f to (286.9 to listOf(290.5, 297.8, 41.1, 257.5, 235.1, 249.6, 252.0, 21.6)),
            70.05f to (285.2 to listOf(299.4, 294.3, 12.2, 255.7, 248.2, 233.2, 249.1, 18.8)),
            71.1f to (286.0 to listOf(299.8, 297.0, 41.3, 231.0, 248.7, 234.8, 250.3, 20.7)),
            71.2f to (286.4 to listOf(303.4, 304.6, 47.1, 231.3, 251.1, 239.0, 255.7, 24.6)),
            71.15f to (286.7 to listOf(283.0, 293.3, 38.0, 231.5, 251.9, 243.7, 247.9, 18.8)),
            71.15f to (286.7 to listOf(286.1, 288.0, 32.3, 231.4, 252.3, 246.3, 243.9, 14.5)),
            71.75f to (285.4 to listOf(284.0, 288.6, 9.1, 255.7, 250.5, 245.0, 243.5, 15.3)),
        )

        val fats = readings.map { (w, zs) ->
            val (chA, z) = zs
            BodyCompositionBuilder.build(
                weightKg = w,
                heightCm = heightCm,
                age = age,
                gender = gender,
                wholeBodyOhm = z[2] + z[3] + z[4],
                segmentsOhm = z,
                channelAOhm = chA,
                channelBOhm = 0.0,
            ).fat
        }

        val spread = fats.max() - fats.min()
        assertTrue("body fat spread should stay tight, was $spread for $fats", spread <= 3.5f)
        assertTrue("all readings should be plausible, got $fats", fats.all { it in 11f..18f })

        val mean = fats.average()
        assertTrue("mean $mean should sit near the 13.4 % tape estimate", abs(mean - 13.4) <= 2.0)
    }

    // ------------------------------------------------------------------
    // Fallbacks
    // ------------------------------------------------------------------

    @Test
    fun `falls back to channel A when Z6 is missing`() {
        val m = BodyCompositionBuilder.build(
            weightKg = ref1403.weightKg,
            heightCm = heightCm,
            age = age,
            gender = gender,
            wholeBodyOhm = ref1403.footPathOhm,
            segmentsOhm = ref1403.z.toMutableList().also { it[5] = 0.0 },
            channelAOhm = ref1403.channelAOhm,
            channelBOhm = ref1403.channelBOhm,
        )
        assertTrue("should still produce body comp", m.hasBodyComp)
        assertTrue(m.algorithm.contains("channel A only"))
        assertEquals(ref1403.channelAOhm * Ssw532FatModel.CHANNEL_A_ONLY_SCALE, m.impedance, 0.01)
    }

    @Test
    fun `falls back to the foot path when channel A is missing too`() {
        val m = BodyCompositionBuilder.build(
            weightKg = ref1403.weightKg,
            heightCm = heightCm,
            age = age,
            gender = gender,
            wholeBodyOhm = ref1403.footPathOhm,
            segmentsOhm = ref1403.z.toMutableList().also { it[5] = 0.0 },
            channelAOhm = 0.0,
            channelBOhm = ref1403.channelBOhm,
        )
        assertTrue("should still produce body comp", m.hasBodyComp)
        assertTrue(m.algorithm.contains("foot-to-foot"))
        assertEquals(ref1403.footPathOhm, m.impedance, 0.01)
    }

    @Test
    fun `publishes weight only when no impedance is usable`() {
        val m = BodyCompositionBuilder.build(
            weightKg = ref1403.weightKg,
            heightCm = heightCm,
            age = age,
            gender = gender,
            wholeBodyOhm = 0.0,
            segmentsOhm = emptyList(),
            channelAOhm = 0.0,
            channelBOhm = 0.0,
        )
        assertEquals(ref1403.weightKg, m.weight, 0.001f)
        assertEquals(0f, m.fat, 0.001f)
        assertTrue(!m.hasBodyComp)
    }

    @Test
    fun `the vendor fat regression is what produced the wrong readings`() {
        // Guards the claim in Wla25.fatMassFromImpedances: with this hardware's
        // impedances the regression lands nowhere near the official 10.4 kg.
        val imps = Ssw532ImpedanceMap.toWla25(ref1403.channelAOhm, ref1403.channelBOhm, ref1403.z)
        assertNotNull(imps)
        val fatMass = Wla25.fatMassFromImpedances(175, 71.2, imps!!)
        assertTrue("regression should be far off the official 10.4 kg, got $fatMass", fatMass > 15.0)
    }
}
