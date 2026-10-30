package com.anant.freescale.bia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ssw532ImpedancesTest {
    private fun hex(s: String) = s.trim().split(" ").map { it.toInt(16).toByte() }.toByteArray()

    /**
     * Frames from a 70.55 kg weigh-in (official Dr. Trust: 14.9 % body fat). Expected vector
     * is what the vendor's own libICBleProtocol decoder produced for these exact bytes.
     */
    private val pkt0 = hex("D7 23 00 A7 6A E4 DB FD 25 01 13 96 00 0A 01 29 0B 7E 0B 04")
    private val pkt1 = hex("D7 23 01 35 0A F9 0B 28 00 F8 09 F6 09 B2 09 5C 09 82 00 0D")

    @Test
    fun `decodes big-endian words across pkt0 and pkt1 like the vendor sdk`() {
        val expected = doubleArrayOf(29.7, 294.2, 286.9, 280.9, 285.6, 24.8, 255.0, 248.2, 239.6, 243.4)
        assertArrayEquals(expected, Ssw532Impedances.decode(pkt0, pkt1)!!, 1e-9)
    }

    @Test
    fun `rejects frames with no impedance`() {
        val empty0 = pkt0.copyOf().also { for (i in 14..18) it[i] = 0 }
        assertNull(Ssw532Impedances.decode(empty0, pkt1))
    }

    @Test
    fun `rejects short frames`() {
        assertNull(Ssw532Impedances.decode(ByteArray(10), pkt1))
    }
}
