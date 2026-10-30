package com.anant.freescale.bia

/**
 * Decodes the 10 impedances the SSW532 sends in its 0x23 measurement frames.
 *
 * The scale streams ten **big-endian** 16-bit words, in tenths of an ohm, starting at
 * byte 14 of pkt0 and continuing at byte 3 of pkt1: pkt0[14..18] ++ pkt1[3..18]. That is
 * exactly the vector the vendor SDK (`libICBleProtocol`) hands to WLA37 as
 * `impendences`, so it is used as-is: no slot remapping, no constants.
 *
 * Verified against the vendor decoder on-device for a 70.55 kg weigh-in: it yields
 * [29.7, 294.2, 286.9, 280.9, 285.6, 24.8, 255.0, 248.2, 239.6, 243.4] ohm, and the native
 * WLA37 turns that into the 14.9 % the vendor app shows.
 */
object Ssw532Impedances {
    const val COUNT = 10

    private const val PKT0_START = 14
    private const val PKT1_START = 3
    private const val PKT_SIZE = 20

    /** Decodes from stored space-separated hex dumps of pkt0 and pkt1. */
    fun decodeHex(pkt0Hex: String, pkt1Hex: String): DoubleArray? {
        fun bytes(s: String): ByteArray? = runCatching {
            s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
        return decode(bytes(pkt0Hex) ?: return null, bytes(pkt1Hex) ?: return null)
    }

    /** @return the 10 impedances in ohm, or null if a frame is short or any path read zero. */
    fun decode(pkt0: ByteArray, pkt1: ByteArray): DoubleArray? {
        if (pkt0.size < PKT_SIZE || pkt1.size < PKT_SIZE) return null
        val stream = ByteArray(PKT_SIZE - 1 - PKT0_START + (PKT_SIZE - 1 - PKT1_START)) // 5 + 16
        var n = 0
        for (i in PKT0_START until PKT_SIZE - 1) stream[n++] = pkt0[i]
        for (i in PKT1_START until PKT_SIZE - 1) stream[n++] = pkt1[i]
        val imps = DoubleArray(COUNT) { k ->
            val hi = stream[2 * k].toInt() and 0xFF
            val lo = stream[2 * k + 1].toInt() and 0xFF
            ((hi shl 8) or lo) / 10.0
        }
        return if (imps.all { it > 0.0 }) imps else null
    }
}
