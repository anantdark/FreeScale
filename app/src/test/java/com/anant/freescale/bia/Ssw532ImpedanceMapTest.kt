package com.anant.freescale.bia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ssw532ImpedanceMapTest {
    @Test
    fun toWla37_matchesPairedCalibration() {
        // FreeScale paired session 2026-09-25 04:50 (72.3 kg / official 14.7%)
        val A = 284.0
        val B = 257.0
        val Z = listOf(298.0, 291.6, 13.1, 254.4, 245.6, 235.8, 246.1, 18.2)
        val imps = Ssw532ImpedanceMap.toWla37(A, B, Z)!!
        assertEquals(28.0, imps[0], 1e-9)
        assertEquals(A, imps[1], 1e-9)
        assertEquals(B * 1.06, imps[2], 1e-9)
        assertEquals(291.6, imps[3], 1e-9) // Z2
        assertEquals(291.6, imps[4], 1e-9) // Z2 (dup)
        assertEquals(24.0, imps[5], 1e-9)
        assertEquals(245.6, imps[6], 1e-9) // Z5
        assertEquals(235.8, imps[7], 1e-9) // Z6
        assertEquals(246.1, imps[8], 1e-9) // Z7
        assertEquals(246.1, imps[9], 1e-9) // Z7 (dup)
    }

    @Test
    fun toWla37_rejectsIncomplete() {
        assertNull(Ssw532ImpedanceMap.toWla37(280.0, 260.0, listOf(1.0, 2.0)))
        assertNull(Ssw532ImpedanceMap.toWla37(0.0, 260.0, List(8) { 200.0 }))
    }
}
