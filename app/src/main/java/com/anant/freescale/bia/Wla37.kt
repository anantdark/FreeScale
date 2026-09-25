package com.anant.freescale.bia

import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithms
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsParams
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsPeopleType
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsSex
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsStandard
import com.icomon.icbodyfatalgorithms.ICBodyFatAlgorithmsType

/**
 * Official Dr. Trust / ICOMON **WLA37** body-fat calc via
 * `libICBodyFatAlgorithms.so` (Frida-confirmed on-device).
 *
 * Requires a 10-slot impedance vector from [Ssw532ImpedanceMap.toWla37].
 */
object Wla37 {
    /**
     * @return body-fat % or null if the native lib is missing / rejects inputs
     */
    fun calcBodyFatPercent(
        weightKg: Double,
        heightCm: Int,
        age: Int,
        sexMale: Boolean,
        imps: DoubleArray,
    ): Double? {
        if (imps.size != 10) return null
        return try {
            val params = ICBodyFatAlgorithmsParams().apply {
                weight = weightKg
                height = heightCm
                this.age = age
                sex = if (sexMale) {
                    ICBodyFatAlgorithmsSex.Male
                } else {
                    ICBodyFatAlgorithmsSex.Female
                }
                peopleType =
                    ICBodyFatAlgorithmsPeopleType.ICBodyFatAlgorithmsPeopleTypeNormal
                standard = ICBodyFatAlgorithmsStandard.ICBodyFatAlgorithmsStandard1
                algType = ICBodyFatAlgorithmsType.ICBodyFatAlgorithmsTypeWLA37
                this.imps = imps.map { it }.toMutableList()
            }
            val result = ICBodyFatAlgorithms.calc(params) ?: return null
            val v = result.bfr
            if (v.isFinite() && v > 0.0 && v < 60.0) v else null
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: Throwable) {
            null
        }
    }
}
