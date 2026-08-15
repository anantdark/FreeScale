package com.anant.freescale

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anant.freescale.ble.BleScanner
import com.anant.freescale.ble.GattClient
import com.anant.freescale.ble.ScannedScale
import com.anant.freescale.data.AppPreferences
import com.anant.freescale.data.GenderType
import com.anant.freescale.data.MeasurePhase
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.ScaleUser
import com.anant.freescale.scales.DrTrustSSW532Handler
import com.anant.freescale.util.BleLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar


data class UiState(
    val scanning: Boolean = false,
    val connected: Boolean = false,
    val status: String = "Tap Connect to find your scale",
    /** Scan hits (internal; not shown while disconnected). */
    val devices: List<ScannedScale> = emptyList(),
    /** Only set while GATT is up; shown in Devices. */
    val connectedDevice: ScannedScale? = null,
    val liveWeightKg: Float? = null,
    val weightLocked: Boolean = false,
    val measurePhase: MeasurePhase = MeasurePhase.Idle,
    /** In-progress / latest packet for the measure screen. */
    val measurement: ScaleMeasurement? = null,
    /** Last completed reading for Home; survives reconnect mid-session. */
    val lastMeasurement: ScaleMeasurement? = null,
    /**
     * Body-comp snapshot for Health bar this process only.
     * Cleared when the app process dies; set again on the next BIA reading.
     */
    val sessionBodyComp: ScaleMeasurement? = null,
    val heightCm: String = "175",
    val ageYears: String = "26",
    val male: Boolean = true,
)

class MeasureViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = AppPreferences(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val debugMode: StateFlow<Boolean> = preferences.debugMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val materialYou: StateFlow<Boolean> = preferences.materialYou
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoConnect: StateFlow<Boolean> = preferences.autoConnect
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val reduceAnimations: StateFlow<Boolean> = preferences.reduceAnimations
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var scanner: BleScanner? = null
    private var gatt: GattClient? = null
    private var handler: DrTrustSSW532Handler? = null
    private var scanTimeoutJob: Job? = null
    private var autoConnectAttempted = false

    init {
        BleLogger.startSession(app)
        BleLogger.i("FreeScale ViewModel init")
        viewModelScope.launch {
            preferences.userProfile.collect { profile ->
                _ui.update {
                    it.copy(
                        heightCm = profile.heightCm,
                        ageYears = profile.ageYears,
                        male = profile.male,
                    )
                }
            }
        }
    }

    fun setHeight(v: String) {
        val filtered = v.filter { c -> c.isDigit() || c == '.' }
        _ui.update { it.copy(heightCm = filtered) }
        persistProfile()
    }

    fun setAge(v: String) {
        val filtered = v.filter { c -> c.isDigit() }
        _ui.update { it.copy(ageYears = filtered) }
        persistProfile()
    }

    fun setMale(v: Boolean) {
        _ui.update { it.copy(male = v) }
        persistProfile()
    }

    private fun persistProfile() {
        val st = _ui.value
        viewModelScope.launch {
            preferences.setUserProfile(st.heightCm, st.ageYears, st.male)
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDebugMode(enabled) }
    }

    fun setMaterialYou(enabled: Boolean) {
        viewModelScope.launch { preferences.setMaterialYou(enabled) }
    }

    fun setAutoConnect(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoConnect(enabled) }
    }

    fun setReduceAnimations(enabled: Boolean) {
        viewModelScope.launch { preferences.setReduceAnimations(enabled) }
    }

    /** True once per process when auto-connect is on and we have not tried yet. */
    fun takeAutoConnectSlot(): Boolean {
        if (autoConnectAttempted) return false
        if (!autoConnect.value) return false
        if (_ui.value.connected || _ui.value.scanning) return false
        autoConnectAttempted = true
        return true
    }

    fun clearLog() = BleLogger.clear()

    fun openDeveloperSettings() {
        val ctx = getApplication<Application>()
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            BleLogger.i("Opened developer settings; enable Bluetooth HCI snoop log")
        } catch (t: Throwable) {
            BleLogger.e("Cannot open developer settings", t)
        }
    }

    fun setStatus(msg: String) = _ui.update { it.copy(status = msg) }

    fun startScan() {
        val ctx = getApplication<Application>()
        val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bm.adapter?.state != BluetoothAdapter.STATE_ON) {
            _ui.update { it.copy(status = "Bluetooth is off. Enable it to connect") }
            BleLogger.e("Bluetooth off")
            return
        }
        scanner?.stop()
        scanTimeoutJob?.cancel()
        _ui.update {
            it.copy(
                scanning = true,
                devices = emptyList(),
                status = "Looking for SSW532…",
            )
        }
        var autoConnectDone = false
        scanner = BleScanner(ctx) { scale ->
            _ui.update { st ->
                val list = st.devices.filterNot { it.address == scale.address } + scale
                st.copy(devices = list.sortedByDescending { it.rssi })
            }
            if (!autoConnectDone &&
                !_ui.value.connected &&
                DrTrustSSW532Handler.matchesName(scale.name)
            ) {
                autoConnectDone = true
                scanTimeoutJob?.cancel()
                BleLogger.i("Auto-connecting to ${scale.name} ${scale.address}")
                connect(scale)
            }
        }
        scanner?.start()
        scanTimeoutJob = viewModelScope.launch {
            delay(SCAN_EMPTY_HINT_MS)
            val st = _ui.value
            if (st.scanning && !st.connected && st.devices.isEmpty()) {
                scanner?.stop()
                _ui.update {
                    it.copy(
                        scanning = false,
                        status = "No scale found. Step on it to wake, then Connect again",
                    )
                }
                BleLogger.i("Scan timed out with no devices")
            }
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        scanner?.stop()
        _ui.update { st ->
            val status = when {
                st.connected -> st.status
                st.devices.isEmpty() -> "No scale found. Step on it to wake, then Connect again"
                else -> "Stopped. Tap Connect to retry"
            }
            st.copy(scanning = false, status = status)
        }
    }

    companion object {
        private const val SCAN_EMPTY_HINT_MS = 8_000L
    }

    fun connect(scale: ScannedScale) {
        stopScan()
        val ctx = getApplication<Application>()
        BleLogger.i("Connecting to ${scale.name} ${scale.address}")
        _ui.update {
            it.copy(
                connected = false,
                connectedDevice = scale,
                status = "Connecting to ${scale.name}…",
                measurement = null,
                liveWeightKg = null,
                weightLocked = false,
                measurePhase = MeasurePhase.Idle,
            )
        }
        gatt?.close()
        val client = GattClient(
            context = ctx,
            onConnectionState = { connected ->
                // Binder thread. hop to main so UI/session reset stays ordered.
                viewModelScope.launch {
                    if (connected) {
                        _ui.update {
                            it.copy(
                                connected = true,
                                status = "Connected. Step on the scale",
                            )
                        }
                    } else {
                        onLinkDown(userInitiated = false)
                    }
                }
            },
            onReady = {
                val user = buildUser()
                BleLogger.i("GATT ready; user age=${user.age} height=${user.bodyHeight} gender=${user.gender}")
                handler?.onConnected(user)
            },
            onNotification = { uuid, data ->
                handler?.onNotification(uuid, data)
            },
            onError = { msg ->
                viewModelScope.launch {
                    _ui.update { it.copy(status = msg) }
                }
            },
        )
        gatt = client
        handler = DrTrustSSW532Handler(
            gatt = client,
            scope = viewModelScope,
            onStatus = { s -> _ui.update { it.copy(status = s) } },
            onLiveWeight = { kg, locked ->
                _ui.update { it.copy(liveWeightKg = kg, weightLocked = locked) }
            },
            onPhase = { phase ->
                _ui.update { it.copy(measurePhase = phase) }
            },
            onMeasurement = { m ->
                _ui.update {
                    it.copy(
                        measurement = m,
                        lastMeasurement = m,
                        sessionBodyComp = if (m.hasBodyComp) m else it.sessionBodyComp,
                        status = "Measurement complete",
                        measurePhase = MeasurePhase.Complete,
                    )
                }
            },
            onLinkLost = { reason ->
                BleLogger.w("ViewModel link lost: $reason")
                viewModelScope.launch {
                    onLinkDown(userInitiated = false)
                }
            },
        )
        client.connect(scale.device)
    }

    fun startMeasurement() {
        handler?.startMeasurement()
            ?: run {
                BleLogger.w("Start measurement but not connected")
                _ui.update { it.copy(status = "Tap Connect first") }
            }
    }

    fun disconnect() {
        BleLogger.i("User disconnect")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        onLinkDown(userInitiated = true)
    }

    /**
     * Scale may drop BLE after weigh-in or idle sleep even if we never tapped Disconnect.
     * Always clear live session UI so Home doesn't look still connected.
     */
    private fun onLinkDown(userInitiated: Boolean) {
        val h = handler
        if (!userInitiated && !_ui.value.connected && h == null) return

        val wasComplete = _ui.value.measurePhase == MeasurePhase.Complete ||
            _ui.value.status.startsWith("Measurement complete")

        handler = null
        h?.onDisconnected()

        _ui.update { st ->
            st.copy(
                connected = false,
                connectedDevice = null,
                devices = emptyList(),
                status = when {
                    userInitiated -> "Disconnected. Tap Connect to reconnect"
                    wasComplete -> "Measurement complete. Connect for another"
                    else -> "Scale disconnected. Tap Connect to reconnect"
                },
                measurePhase = MeasurePhase.Idle,
                liveWeightKg = null,
                weightLocked = false,
            )
        }
    }

    private fun buildUser(): ScaleUser {
        val st = _ui.value
        val age = st.ageYears.toIntOrNull()?.coerceIn(5, 100) ?: 30
        val height = st.heightCm.toFloatOrNull()?.coerceIn(100f, 220f) ?: 170f
        val cal = Calendar.getInstance().apply { add(Calendar.YEAR, -age) }
        return ScaleUser(
            bodyHeight = height,
            birthday = cal.time,
            gender = if (st.male) GenderType.MALE else GenderType.FEMALE,
        )
    }

    override fun onCleared() {
        stopScan()
        gatt?.close()
        super.onCleared()
    }
}
