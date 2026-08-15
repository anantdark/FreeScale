package com.anant.freescale.scales

import android.bluetooth.BluetoothGatt
import android.os.SystemClock
import com.anant.freescale.bia.BodyCompositionBuilder
import com.anant.freescale.ble.GattClient
import com.anant.freescale.data.MeasurePhase
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.ScaleUser
import com.anant.freescale.util.BleLogger
import com.anant.freescale.util.toHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class DrTrustSSW532Handler(
    private val gatt: GattClient,
    private val scope: CoroutineScope,
    private val onStatus: (String) -> Unit,
    private val onLiveWeight: (Float, locked: Boolean) -> Unit,
    private val onPhase: (MeasurePhase) -> Unit,
    private val onMeasurement: (ScaleMeasurement) -> Unit,
    private val onLinkLost: (reason: String) -> Unit,
) {
    private val SERVICE: UUID = GattClient.uuid16(0xFFB0)
    private val CHAR_CMD: UUID = GattClient.uuid16(0xFFB1)
    private val CHAR_WEIGHT: UUID = GattClient.uuid16(0xFFB2)
    private val CHAR_BC: UUID = GattClient.uuid16(0xFFB3)

    private enum class State { WAITING_SESSION, WAITING_CONFIRM, MEASURING }

    private var state = State.WAITING_SESSION
    private var sessionId: Int = 0x00
    private var user: ScaleUser = ScaleUser()
    private var phase: MeasurePhase = MeasurePhase.Idle

    private var pendingWeightKg: Float = 0f
    private var savedWeightKg: Float = 0f
    private var bodyCompPublished = false
    private var pkt0Valid = false
    private var z3 = 0.0
    private var z4 = 0.0
    private var z5 = 0.0
    private var zSegments: List<Double> = emptyList()
    private var channelAOhm = 0.0
    private var channelBOhm = 0.0
    private var lastPkt0Cmd = 0
    private var lastPkt0ValidFlag = 0
    private var pkt0Hex = ""
    private var pkt1Hex = ""
    private var pkt2Hex = ""
    private var gotImpedance = false
    private var isLiveWeightLocked = false
    private var isLiveMeasurement = false
    private var profileSent = false
    private var fallbackJob: Job? = null
    private var keepAliveJob: Job? = null
    private var lastRxElapsedMs = 0L
    private val linkLostFired = AtomicBoolean(false)

    fun onConnected(user: ScaleUser) {
        this.user = user
        state = State.WAITING_SESSION
        savedWeightKg = 0f
        bodyCompPublished = false
        isLiveWeightLocked = false
        isLiveMeasurement = false
        profileSent = false
        linkLostFired.set(false)
        fallbackJob?.cancel()
        fallbackJob = null
        reset()
        noteRx()
        BleLogger.i("SSW532 onConnected; subscribe INDICATE FFB3 first")
        onStatus("Linking to scale…")
        // Indicate FFB3 first
        gatt.enableCccd(SERVICE, CHAR_BC, indicate = true)
        setPhase(MeasurePhase.Ready)
        startKeepAlive()
    }

    fun onDisconnected() {
        stopKeepAlive()
        fallbackJob?.cancel()
        fallbackJob = null
        if (!bodyCompPublished) {
            val fallback = when {
                savedWeightKg > 0f -> savedWeightKg
                isLiveWeightLocked && pendingWeightKg > 0f -> pendingWeightKg
                else -> 0f
            }
            if (fallback > 0f) {
                BleLogger.i("Disconnect fallback weight=$fallback")
                onMeasurement(
                    ScaleMeasurement().apply {
                        dateTime = Date()
                        weight = fallback
                    }
                )
            }
        }
        reset()
        profileSent = false
        setPhase(MeasurePhase.Idle)
    }

    fun onNotification(characteristic: UUID, data: ByteArray) {
        noteRx()
        when (characteristic) {
            CHAR_BC -> onBcFrame(data)
            CHAR_WEIGHT -> onWeightFrame(data)
            else -> BleLogger.w("Unexpected RX ${GattClient.shortUuid(characteristic)}")
        }
    }

    /**
     * Arms the scale for a live weigh-in (profile + start candidates).
     * Called automatically after connect; no Measure button in the UI.
     */
    fun startMeasurement() {
        BleLogger.i("=== START MEASUREMENT (profileSent=$profileSent sessionId=0x${sessionId.toString(16)}) ===")
        onStatus("Arming scale…")
        if (!profileSent) {
            BleLogger.w("Profile not sent yet; sending profile first")
            sendUserProfile(user)
            profileSent = true
        } else {
            // Re-sync profile then candidate start frames
            sendUserProfile(user)
        }
        writeStartCandidateBd()
        writeStartCandidateBa()
        onStatus("Armed. Step on barefoot; hold handlebars for BIA")
        state = State.MEASURING
        setPhase(MeasurePhase.Armed)
    }

    private fun onBcFrame(d: ByteArray) {
        if (d.size < 20) {
            BleLogger.w("FFB3 short frame ${d.size}b")
            return
        }
        when (d[1].toUByte().toInt()) {
            0x18 -> onSetupFrame(d)
            0x23 -> if (state == State.MEASURING) onMeasurementFrame(d)
            else -> BleLogger.d("FFB3 type=0x${d[1].toUByte().toString(16)}")
        }
    }

    private fun onSetupFrame(d: ByteArray) {
        when (d[2].toUByte().toInt()) {
            0x00 -> {
                sessionId = d[0].toUByte().toInt()
                BleLogger.i("setup0 sessionId=0x${sessionId.toString(16)}; subscribe NOTIFY FFB2")
                onStatus("Listening for weight…")
                gatt.enableCccd(SERVICE, CHAR_WEIGHT, indicate = false)
                state = State.WAITING_CONFIRM
            }
            0x01 -> {
                BleLogger.i("setup1; sending user profile + arm")
                onStatus("Ready. Step on the scale; hold handlebars for BIA")
                sendUserProfile(user)
                profileSent = true
                state = State.MEASURING
                // Auto-arm (Measure button removed). BD/BA primes live A3/A7 sessions.
                writeStartCandidateBd()
                writeStartCandidateBa()
                setPhase(MeasurePhase.Armed)
            }
            else -> BleLogger.d("setup sub=${d[2].toUByte().toInt()}")
        }
    }

    private fun onWeightFrame(d: ByteArray) {
        if (d.size < 9) return
        if (d[1].toUByte().toInt() != 0x07) return
        if (d[3].toUByte().toInt() != 0xA2) return
        val stability = d[4].toUByte().toInt()
        val kg = readBE24(d, 6) / 1000.0f
        BleLogger.d("live weight=$kg stability=0x${stability.toString(16)}")
        if (kg > 0f) {
            val locked = stability == 0x03
            if (locked) {
                pendingWeightKg = kg
                isLiveWeightLocked = true
                if (!gotImpedance) setPhase(MeasurePhase.WeightStable)
            } else if (!gotImpedance) {
                setPhase(MeasurePhase.Weighing)
            }
            onLiveWeight(kg, locked)
        } else if (kg < 2.0f && savedWeightKg > 0f && !bodyCompPublished) {
            BleLogger.i("step-off; publish savedWeightKg=$savedWeightKg")
            bodyCompPublished = true
            onMeasurement(ScaleMeasurement().apply { dateTime = Date(); weight = savedWeightKg })
            savedWeightKg = 0f
            writeTeardownAck()
            gatt.disconnect()
        }
    }

    private fun onMeasurementFrame(d: ByteArray) {
        when (d[2].toUByte().toInt()) {
            0x00 -> {
                val cmd = d[3].toUByte().toInt()
                isLiveMeasurement = (cmd == 0xA3 || cmd == 0xA7)
                BleLogger.i("pkt0 cmd=0x${cmd.toString(16)} live=$isLiveMeasurement")
                if (!isLiveMeasurement) {
                    pkt0Valid = false
                    return
                }
                pkt0Valid = d[14].toUByte().toInt() == 0x01
                if (!pkt0Valid) return
                val kg = readBE24(d, 9) / 1000.0f
                if (kg > 0f) pendingWeightKg = kg
                // Whole-body channels A/B at [15-16] / [17-18]
                channelAOhm = readLE16(d, 15) / 10.0
                channelBOhm = readLE16(d, 17) / 10.0
                lastPkt0Cmd = cmd
                lastPkt0ValidFlag = d[14].toUByte().toInt()
                pkt0Hex = d.toHex()
                BleLogger.i("pkt0 channels A=$channelAOhm B=$channelBOhm Ω")
                if (channelAOhm > 0.0 || channelBOhm > 0.0) {
                    setPhase(MeasurePhase.MeasuringBia)
                }
            }
            0x01 -> {
                if (!pkt0Valid || !isLiveMeasurement) return
                val segs = mutableListOf<Double>()
                for (i in 0 until 8) {
                    segs += readLE16(d, 3 + i * 2) / 10.0
                }
                zSegments = segs
                z3 = segs.getOrElse(2) { 0.0 }
                z4 = segs.getOrElse(3) { 0.0 }
                z5 = segs.getOrElse(4) { 0.0 }
                gotImpedance = true
                pkt1Hex = d.toHex()
                setPhase(MeasurePhase.MeasuringBia)
                BleLogger.i(
                    "pkt1 Z=${segs.joinToString { "%.1f".format(it) }} " +
                        "footPath=${z3 + z4 + z5}"
                )
            }
            0x02 -> {
                pkt2Hex = d.toHex()
                BleLogger.i("pkt2 end locked=$isLiveWeightLocked live=$isLiveMeasurement gotZ=$gotImpedance")
                if (!isLiveWeightLocked || !isLiveMeasurement) {
                    writeTeardownAck()
                    BleLogger.i("Skipping cached/non-live replay")
                } else if (pendingWeightKg > 0f) {
                    if (pkt0Valid && gotImpedance) {
                        publishWithBodyComp()
                    } else {
                        savedWeightKg = pendingWeightKg
                        setPhase(MeasurePhase.WeightStable)
                        onStatus("Weight locked. Hold handlebars for BIA")
                        fallbackJob?.cancel()
                        fallbackJob = scope.launch {
                            delay(5000)
                            if (!bodyCompPublished && savedWeightKg > 0f) {
                                BleLogger.i("No BIA in 5s; weight-only")
                                bodyCompPublished = true
                                onMeasurement(
                                    ScaleMeasurement().apply {
                                        dateTime = Date()
                                        weight = savedWeightKg
                                    }
                                )
                                savedWeightKg = 0f
                                setPhase(MeasurePhase.Complete)
                                writeTeardownAck()
                                gatt.disconnect()
                            }
                        }
                    }
                }
                reset()
            }
        }
    }

    private fun publishWithBodyComp() {
        fallbackJob?.cancel()
        fallbackJob = null
        val wholeBodyZ = z3 + z4 + z5
        val m = BodyCompositionBuilder.build(
            weightKg = pendingWeightKg,
            heightCm = user.bodyHeight,
            age = user.age,
            gender = user.gender,
            wholeBodyOhm = wholeBodyZ,
            trunkOhm = z3,
            segmentsOhm = zSegments,
            channelAOhm = channelAOhm,
            channelBOhm = channelBOhm,
            pkt0Cmd = lastPkt0Cmd,
            pkt0ValidFlag = lastPkt0ValidFlag,
            pkt0Hex = pkt0Hex,
            pkt1Hex = pkt1Hex,
            pkt2Hex = pkt2Hex,
        )
        if (m.fat <= 0f) {
            BleLogger.w("body comp sanity fail fatPct=${m.fat} Z=$wholeBodyZ")
            savedWeightKg = pendingWeightKg
            gatt.disconnect()
            return
        }
        savedWeightKg = 0f
        bodyCompPublished = true
        BleLogger.i(
            "PUBLISH w=${m.weight} bmi=${m.bmi} fat=${m.fat} water=${m.water} " +
                "muscle=${m.muscle} protein=${m.protein} bone=${m.bone} " +
                "vf=${m.visceralFat} age=${m.bodyAge} score=${m.bodyScore} Z=${m.impedance}"
        )
        onStatus("Measurement complete")
        onMeasurement(m)
        setPhase(MeasurePhase.Complete)
        writeTeardownAck()
        gatt.disconnect()
    }

    private fun reset() {
        pendingWeightKg = 0f
        pkt0Valid = false
        z3 = 0.0
        z4 = 0.0
        z5 = 0.0
        zSegments = emptyList()
        channelAOhm = 0.0
        channelBOhm = 0.0
        lastPkt0Cmd = 0
        lastPkt0ValidFlag = 0
        pkt0Hex = ""
        pkt1Hex = ""
        pkt2Hex = ""
        gotImpedance = false
        isLiveMeasurement = false
    }

    private fun sendUserProfile(user: ScaleUser) {
        val ts = (System.currentTimeMillis() / 1000L).toInt()
        writePktA()
        writeB0(slot = 0x01, user = user, ts = ts)
        writeB1(slot = 0x01)
    }

    private fun writePktA(onResult: ((Int) -> Unit)? = null) {
        val payload = byteArrayOf(
            0x00, 0x03, 0x00, 0xB0.toByte(), sessionId.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        payload[19] = checksum(payload)
        BleLogger.i("TX profile pktA")
        writeCmd(payload, onResult)
    }

    private fun writeB0(slot: Int, user: ScaleUser, ts: Int) {
        val h = user.bodyHeight.toInt().coerceIn(100, 220)
        val age = user.age.coerceIn(0, 127)
        val payload = ByteArray(20)
        payload[0] = slot.toByte()
        payload[1] = 0x1A
        payload[2] = 0x00
        payload[3] = 0xB8.toByte()
        payload[4] = (ts shr 24).toByte()
        payload[5] = (ts shr 16).toByte()
        payload[6] = (ts shr 8).toByte()
        payload[7] = ts.toByte()
        payload[8] = 0x01
        payload[9] = 0x4A
        payload[10] = 0x01
        payload[11] = h.toByte()
        payload[12] = 0x17
        payload[13] = 0x70
        payload[14] = (0x80 or age).toByte()
        payload[15] = 0x13
        payload[16] = 0x88.toByte()
        payload[17] = 0x0F
        payload[18] = 0x00
        payload[19] = checksum(payload)
        BleLogger.i("TX profile B0 height=$h age=$age")
        writeCmd(payload)
    }

    private fun writeB1(slot: Int) {
        val payload = byteArrayOf(
            slot.toByte(), 0x1A, 0x01, 0x00, 0x00, 0x00, 0x06,
            0x69, 0x63, 0x6F, 0x6D, 0x6F, 0x6E, // "icomon"
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        payload[19] = checksum(payload)
        BleLogger.i("TX profile B1 icomon")
        writeCmd(payload)
    }

    /** Candidate start frame (ICOMON/SACOMA-style BD). verify via HCI vs official app. */
    private fun writeStartCandidateBd() {
        val payload = ByteArray(20)
        payload[0] = 0x01
        payload[1] = 0x03
        payload[2] = 0x00
        payload[3] = 0xBD.toByte()
        payload[4] = sessionId.toByte()
        payload[19] = checksum(payload)
        BleLogger.i("TX candidate START BD (experimental)")
        writeCmd(payload)
    }

    private fun writeStartCandidateBa() {
        val payload = ByteArray(20)
        payload[0] = 0x01
        payload[1] = 0x03
        payload[2] = 0x00
        payload[3] = 0xBA.toByte()
        payload[4] = sessionId.toByte()
        payload[19] = checksum(payload)
        BleLogger.i("TX candidate START BA (experimental)")
        writeCmd(payload)
    }

    private fun writeTeardownAck() {
        val payload = ByteArray(20)
        payload[0] = 0x04
        payload[1] = 0x03
        payload[2] = 0x00
        payload[3] = 0xB0.toByte()
        payload[4] = sessionId.toByte()
        payload[19] = checksum(payload)
        BleLogger.i("TX teardown ACK")
        writeCmd(payload)
    }

    private fun checksum(buf: ByteArray): Byte {
        var s = 0
        for (i in 3..18) s += buf[i].toUByte().toInt()
        return (s % 32).toByte()
    }

    private fun writeCmd(payload: ByteArray, onResult: ((Int) -> Unit)? = null) =
        gatt.write(SERVICE, CHAR_CMD, payload, withResponse = true, onResult = onResult)

    private fun setPhase(p: MeasurePhase) {
        phase = p
        onPhase(p)
    }

    private fun noteRx() {
        lastRxElapsedMs = SystemClock.elapsedRealtime()
    }

    private fun isActivelyMeasuring(): Boolean =
        phase == MeasurePhase.Weighing ||
            phase == MeasurePhase.WeightStable ||
            phase == MeasurePhase.MeasuringBia

    private fun startKeepAlive() {
        stopKeepAlive()
        keepAliveJob = scope.launch {
            while (true) {
                delay(KEEPALIVE_INTERVAL_MS)
                if (!gatt.isOpen()) {
                    declareLinkLost("GATT closed")
                    break
                }
                val quietFor = SystemClock.elapsedRealtime() - lastRxElapsedMs
                if (quietFor >= RX_TIMEOUT_MS) {
                    declareLinkLost("No scale traffic for ${quietFor}ms")
                    break
                }
                if (isActivelyMeasuring()) continue

                BleLogger.d("Keepalive ping (idle ${quietFor}ms since RX)")
                val ok = pingScale()
                if (!ok) {
                    declareLinkLost("Keepalive write failed")
                    break
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private suspend fun pingScale(): Boolean {
        var result = BluetoothGatt.GATT_FAILURE
        val done = AtomicBoolean(false)
        val payload = byteArrayOf(
            0x00, 0x03, 0x00, 0xB0.toByte(), sessionId.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        payload[19] = checksum(payload)
        writeCmd(payload) { status ->
            result = status
            done.set(true)
        }
        val deadline = SystemClock.elapsedRealtime() + PING_TIMEOUT_MS
        while (!done.get() && SystemClock.elapsedRealtime() < deadline) {
            delay(50)
        }
        if (!done.get()) {
            BleLogger.w("Keepalive ping timed out")
            return false
        }
        return result == BluetoothGatt.GATT_SUCCESS
    }

    private fun declareLinkLost(reason: String) {
        if (!linkLostFired.compareAndSet(false, true)) return
        BleLogger.w("Link lost: $reason")
        stopKeepAlive()
        onStatus("Scale connection lost. Tap Connect to reconnect")
        onLinkLost(reason)
        try {
            gatt.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun readBE24(d: ByteArray, off: Int) =
        (d[off].toUByte().toInt() shl 16) or
            (d[off + 1].toUByte().toInt() shl 8) or
            d[off + 2].toUByte().toInt()

    private fun readLE16(d: ByteArray, off: Int) =
        d[off].toUByte().toInt() or (d[off + 1].toUByte().toInt() shl 8)

    companion object {
        private const val KEEPALIVE_INTERVAL_MS = 5_000L
        private const val RX_TIMEOUT_MS = 8_000L
        private const val PING_TIMEOUT_MS = 3_000L

        fun matchesName(name: String?): Boolean {
            val n = name?.lowercase() ?: return false
            return n == "ssw532" || n.startsWith("ssw") || n.contains("fg2211") ||
                n.contains("dr trust") || n.contains("drtrust")
        }
    }
}
