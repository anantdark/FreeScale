package com.anant.freescale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.anant.freescale.scales.DrTrustSSW532Handler
import com.anant.freescale.util.BleLogger

data class ScannedScale(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
)

@SuppressLint("MissingPermission")
class BleScanner(
    context: Context,
    private val onDevice: (ScannedScale) -> Unit,
) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner
    private var scanning = false
    private val seen = HashSet<String>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            val addr = result.device.address
            val hasFfb0 = result.scanRecord?.serviceUuids?.any {
                it.uuid == GattClient.uuid16(0xFFB0)
            } == true
            val match = DrTrustSSW532Handler.matchesName(name) || hasFfb0
            if (!match) return
            val display = name ?: "Unknown"
            val first = seen.add(addr)
            if (first) {
                BleLogger.i("SCAN hit name=$display addr=$addr rssi=${result.rssi}")
            }
            onDevice(ScannedScale(result.device, display, addr, result.rssi))
        }

        override fun onScanFailed(errorCode: Int) {
            BleLogger.e("Scan failed code=$errorCode")
            scanning = false
        }
    }

    fun start() {
        if (scanning) return
        if (adapter?.isEnabled != true) {
            BleLogger.e("Bluetooth adapter off")
            return
        }
        BleLogger.i("BLE scan start")
        seen.clear()
        scanning = true
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, callback)
    }

    fun stop() {
        if (!scanning) return
        BleLogger.i("BLE scan stop")
        try {
            scanner?.stopScan(callback)
        } catch (t: Throwable) {
            BleLogger.w("stopScan: ${t.message}")
        }
        scanning = false
    }
}
