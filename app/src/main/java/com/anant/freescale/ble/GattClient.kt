package com.anant.freescale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.anant.freescale.util.BleLogger
import com.anant.freescale.util.toHex
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Serialised GATT client: writes and CCCD enables run one-at-a-time.
 * FFB3 must use INDICATION (not NOTIFY). critical for SSW532.
 */
@SuppressLint("MissingPermission")
class GattClient(
    private val context: Context,
    private val onConnectionState: (connected: Boolean) -> Unit,
    private val onReady: () -> Unit,
    private val onNotification: (characteristic: UUID, data: ByteArray) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var gatt: BluetoothGatt? = null
    private val queue = ConcurrentLinkedQueue<() -> Unit>()
    private val busy = AtomicBoolean(false)
    private val pendingWriteResult = AtomicReference<((Int) -> Unit)?>(null)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            BleLogger.i("GATT state status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onError("Connection failed status=$status")
                close()
                onConnectionState(false)
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onConnectionState(true)
                    BleLogger.i("Connected; discovering services (no requestMtu)")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    BleLogger.i("Disconnected")
                    drainClear()
                    onConnectionState(false)
                    close()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            BleLogger.i("Services discovered status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onError("Service discovery failed: $status")
                return
            }
            for (svc in g.services) {
                BleLogger.d("Service ${svc.uuid}")
                for (ch in svc.characteristics) {
                    val props = ch.properties
                    BleLogger.d(
                        "  Char ${ch.uuid} props=0x${props.toString(16)} " +
                            "N=${props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0} " +
                            "I=${props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0} " +
                            "W=${props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0}"
                    )
                }
            }
            onReady()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            BleLogger.hex("RX ${shortUuid(characteristic.uuid)}", value)
            onNotification(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            BleLogger.hex("RX ${shortUuid(characteristic.uuid)}", value)
            onNotification(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            BleLogger.d("TX complete ${shortUuid(characteristic.uuid)} status=$status")
            pendingWriteResult.getAndSet(null)?.invoke(status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onError("Write failed status=$status")
            }
            next()
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            BleLogger.d("CCCD write ${shortUuid(descriptor.characteristic.uuid)} status=$status")
            next()
        }
    }

    fun connect(device: BluetoothDevice) {
        BleLogger.i("connectGatt ${device.address} name=${device.name}")
        close()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
    }

    fun disconnect() {
        BleLogger.i("disconnect()")
        gatt?.disconnect()
    }

    fun close() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        drainClear()
    }

    fun isOpen(): Boolean = gatt != null

    /** Enable notifications (FFB2) or indications (FFB3). */
    fun enableCccd(service: UUID, characteristic: UUID, indicate: Boolean) {
        enqueue {
            val g = gatt ?: return@enqueue next()
            val ch = g.getService(service)?.getCharacteristic(characteristic)
            if (ch == null) {
                BleLogger.e("Missing characteristic $characteristic")
                onError("Missing $characteristic")
                return@enqueue next()
            }
            val ok = g.setCharacteristicNotification(ch, true)
            BleLogger.i(
                "setCharacteristicNotification ${shortUuid(characteristic)} " +
                    "indicate=$indicate ok=$ok"
            )
            val cccd = ch.getDescriptor(CCCD) ?: run {
                BleLogger.e("No CCCD on ${shortUuid(characteristic)}")
                return@enqueue next()
            }
            val value = if (indicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            BleLogger.hex("CCCD ${shortUuid(characteristic)}", value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, value)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
    }

    fun write(
        service: UUID,
        characteristic: UUID,
        payload: ByteArray,
        withResponse: Boolean = true,
        onResult: ((status: Int) -> Unit)? = null,
    ) {
        enqueue {
            val g = gatt
            if (g == null) {
                onResult?.invoke(BluetoothGatt.GATT_FAILURE)
                return@enqueue next()
            }
            val ch = g.getService(service)?.getCharacteristic(characteristic)
            if (ch == null) {
                BleLogger.e("Write failed; missing ${shortUuid(characteristic)}")
                onResult?.invoke(BluetoothGatt.GATT_FAILURE)
                return@enqueue next()
            }
            BleLogger.hex("TX ${shortUuid(characteristic)}", payload)
            pendingWriteResult.set(onResult)
            val writeType = if (withResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, payload, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.value = payload
                @Suppress("DEPRECATION")
                ch.writeType = writeType
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
            if (!started) {
                BleLogger.e("writeCharacteristic rejected")
                pendingWriteResult.getAndSet(null)?.invoke(BluetoothGatt.GATT_FAILURE)
                next()
            } else if (!withResponse) {
                // No callback for write-without-response; treat as queued OK.
                pendingWriteResult.getAndSet(null)?.invoke(BluetoothGatt.GATT_SUCCESS)
                next()
            }
        }
    }

    private fun enqueue(op: () -> Unit) {
        queue.add(op)
        pump()
    }

    private fun pump() {
        if (!busy.compareAndSet(false, true)) return
        val op = queue.poll()
        if (op == null) {
            busy.set(false)
            return
        }
        try {
            op()
        } catch (t: Throwable) {
            BleLogger.e("GATT op failed", t)
            pendingWriteResult.getAndSet(null)?.invoke(BluetoothGatt.GATT_FAILURE)
            busy.set(false)
            pump()
        }
    }

    private fun next() {
        busy.set(false)
        pump()
    }

    private fun drainClear() {
        queue.clear()
        busy.set(false)
        pendingWriteResult.getAndSet(null)?.invoke(BluetoothGatt.GATT_FAILURE)
    }

    companion object {
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun shortUuid(u: UUID): String {
            val s = u.toString()
            return if (s.startsWith("0000") && s.endsWith("-0000-1000-8000-00805f9b34fb")) {
                s.substring(4, 8)
            } else s
        }

        fun uuid16(short: Int): UUID =
            UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", short))
    }
}
