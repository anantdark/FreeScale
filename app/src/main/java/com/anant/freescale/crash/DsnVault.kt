package com.anant.freescale.crash

import java.util.Base64

/** XOR + Base64 decode for build-baked secrets (Sentry DSN). */
object DsnVault {
    fun decode(base64Blob: String, maskSeed: String): String {
        val masked = Base64.getDecoder().decode(base64Blob)
        val mask = maskSeed.toByteArray(Charsets.UTF_8)
        if (mask.isEmpty()) return String(masked, Charsets.UTF_8)
        val plain = ByteArray(masked.size) { i ->
            (masked[i].toInt() xor mask[i % mask.size].toInt()).toByte()
        }
        return String(plain, Charsets.UTF_8)
    }
}
