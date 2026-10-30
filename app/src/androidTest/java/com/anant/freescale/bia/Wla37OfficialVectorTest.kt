package com.anant.freescale.bia

import com.anant.freescale.data.GenderType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the real native WLA37 on an arm64 device against a weigh-in the Dr. Trust app
 * reported as 14.9 % body fat (70.55 kg, BMI 23.0). The frames are the scale's own bytes.
 */
class Wla37OfficialVectorTest {
    private fun hex(s: String) = s.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun officialReadingMatches() {
        val pkt0 = hex("D7 23 00 A7 6A E4 DB FD 25 01 13 96 00 0A 01 29 0B 7E 0B 04")
        val pkt1 = hex("D7 23 01 35 0A F9 0B 28 00 F8 09 F6 09 B2 09 5C 09 82 00 0D")
        val m = BodyCompositionBuilder.build(
            weightKg = 70.55f,
            heightCm = 175f,
            age = 26,
            gender = GenderType.MALE,
            impedances = Ssw532Impedances.decode(pkt0, pkt1),
        )
        assertEquals(14.9f, m.fat, 0.05f)
        assertEquals(23.0f, m.bmi, 0.05f)
    }
}
