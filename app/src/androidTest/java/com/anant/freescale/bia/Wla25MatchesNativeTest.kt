package com.anant.freescale.bia

import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithms
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsParams
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsPeopleType
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsSex
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsStandard
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The vendor's native WLA37 returns every metric, not just body fat. [Wla25.derive], fed that
 * body fat, must reproduce them all; this is what lets the app use one chain for the rest.
 */
class Wla25MatchesNativeTest {
    @Test
    fun deriveReproducesNativeMetrics() {
        val imps = doubleArrayOf(29.7, 294.2, 286.9, 280.9, 285.6, 24.8, 255.0, 248.2, 239.6, 243.4)
        val r = ICBodyFatAlgorithms.calc(
            ICBodyFatAlgorithmsParams().apply {
                weight = 70.55
                height = 175
                age = 26
                sex = ICBodyFatAlgorithmsSex.Male
                peopleType = ICBodyFatAlgorithmsPeopleType.ICBodyFatAlgorithmsPeopleTypeNormal
                standard = ICBodyFatAlgorithmsStandard.ICBodyFatAlgorithmsStandard1
                algType = ICBodyFatAlgorithmsType.ICBodyFatAlgorithmsTypeWLA37
                this.imps = imps.toMutableList()
            },
        )!!
        val w = Wla25.derive(175, 70.55, 26, 1, r.bfr, imps)

        assertEquals("bmi", r.bmi, w.bmi, 0.05)
        assertEquals("muscle", r.muscle, w.musclePercent, 0.05)
        assertEquals("water", r.water, w.bodyWaterPercent, 0.05)
        assertEquals("bone", r.bone, w.boneMassKg, 0.05)
        assertEquals("protein", r.protein, w.proteinPercent, 0.05)
        assertEquals("visceral", r.vfal, w.visceralFat, 0.05)
        assertEquals("subcutaneous", r.subcutfat, w.subcutaneousFatPercent, 0.05)
        assertEquals("skeletal muscle", r.sm, w.skeletalMusclePercent, 0.05)
        assertEquals("bmr", r.bmr, w.bmrKcal)
        assertEquals("body age", r.age, w.metabolicAge)
        assertEquals("score", r.bodyScore, w.bodyScore, 0.5)
        assertEquals("trunk fat kg", r.trunkBodyfatMass, w.segments.first { it.name == "Trunk" }.fatKg, 0.01)
    }
}
