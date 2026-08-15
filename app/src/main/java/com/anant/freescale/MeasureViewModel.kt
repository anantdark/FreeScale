package com.anant.freescale

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anant.freescale.ble.BleScanner
import com.anant.freescale.ble.GattClient
import com.anant.freescale.ble.ScannedScale
import com.anant.freescale.bridge.FitBuddyBridge
import com.anant.freescale.crash.CrashReporter
import com.anant.freescale.crash.HeartbeatInfo
import com.anant.freescale.crash.HeartbeatKind
import com.anant.freescale.crash.HeartbeatScheduler
import com.anant.freescale.data.AppPreferences
import com.anant.freescale.data.GenderType
import com.anant.freescale.data.MeasurePhase
import com.anant.freescale.data.ScaleMeasurement
import com.anant.freescale.data.ScaleUser
import com.anant.freescale.data.backup.MeasurementBackupJson
import com.anant.freescale.data.db.MeasurementRepository
import com.anant.freescale.data.remote.UpdateCheckResult
import com.anant.freescale.data.remote.UpdateChecker
import com.anant.freescale.scales.DrTrustSSW532Handler
import com.anant.freescale.ui.loading.LoadingAnimChoice
import com.anant.freescale.ui.progress.PeriodUnit
import com.anant.freescale.ui.progress.ProgressMetric
import com.anant.freescale.ui.progress.ProgressPeriod
import com.anant.freescale.util.BackupShare
import com.anant.freescale.util.BleLogger
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date

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
     * Weight-only reading awaiting user confirm before persist.
     * Null when idle or after a full BIA save.
     */
    val pendingWeightOnly: ScaleMeasurement? = null,
    /**
     * Body-comp snapshot for Health bar this process only.
     * Cleared when the app process dies; set again on the next BIA reading.
     */
    val sessionBodyComp: ScaleMeasurement? = null,
    val heightCm: String = "175",
    val ageYears: String = "26",
    val male: Boolean = true,
)

/** State of the Settings update-check flow / update prompt. */
data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: UpdateCheckResult.Available? = null,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
)

/** Progress tab: period window + selected chart metric. */
data class ProgressUiState(
    val periodUnit: PeriodUnit = PeriodUnit.Week,
    val periodAnchor: LocalDate = LocalDate.now(),
    val metric: ProgressMetric = ProgressMetric.Weight,
) {
    val period: ProgressPeriod
        get() = ProgressPeriod(unit = periodUnit, anchor = periodAnchor)
}

enum class BackupImportMode {
    /** Keep existing readings; add only those with new timestamps. */
    Merge,
    /** Wipe local history, then load the backup. */
    Replace,
}

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

data class FitBuddyUiState(
    val available: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class MeasureViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = AppPreferences(app)
    private val measurements = MeasurementRepository(app)
    private val updateChecker = UpdateChecker()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _progress = MutableStateFlow(ProgressUiState())
    val progress: StateFlow<ProgressUiState> = _progress.asStateFlow()

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _fitBuddyState = MutableStateFlow(FitBuddyUiState())
    val fitBuddyState: StateFlow<FitBuddyUiState> = _fitBuddyState.asStateFlow()

    val measurementHistory: StateFlow<List<ScaleMeasurement>> =
        measurements.observeAllNewestFirst()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val measurementCount: StateFlow<Int> =
        measurements.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val periodReadings: StateFlow<List<ScaleMeasurement>> =
        _progress
            .map { it.period }
            .distinctUntilChanged()
            .flatMapLatest { period ->
                measurements.observeInRange(
                    fromEpochMs = period.startEpochMs(),
                    toEpochMs = period.endExclusiveEpochMs(),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val debugMode: StateFlow<Boolean> = preferences.debugMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val materialYou: StateFlow<Boolean> = preferences.materialYou
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoConnect: StateFlow<Boolean> = preferences.autoConnect
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val reduceAnimations: StateFlow<Boolean> = preferences.reduceAnimations
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val crashReportingEnabled: StateFlow<Boolean> = preferences.crashReportingEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppPreferences.defaultCrashReportingEnabled(),
        )

    val autoCheckUpdates: StateFlow<Boolean> = preferences.autoCheckUpdates
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppPreferences.defaultAutoCheckUpdates(),
        )

    val developerModeUnlocked: StateFlow<Boolean> = preferences.developerModeUnlocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val readingAnimationChoice: StateFlow<String> = preferences.readingAnimationChoice
        .stateIn(viewModelScope, SharingStarted.Eagerly, LoadingAnimChoice.RANDOM)

    val forceShowLoadingAnimations: StateFlow<Boolean> = preferences.forceShowLoadingAnimations
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val shareToFitBuddy: StateFlow<Boolean> = preferences.shareToFitBuddy
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var scanner: BleScanner? = null
    private var gatt: GattClient? = null
    private var handler: DrTrustSSW532Handler? = null
    private var scanTimeoutJob: Job? = null
    private var autoConnectAttempted = false

    init {
        BleLogger.startSession(app)
        BleLogger.i("FreeScale ViewModel init")
        refreshFitBuddyAvailability()
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
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { measurements.latest() }
            if (latest != null) {
                _ui.update {
                    it.copy(
                        lastMeasurement = latest,
                        sessionBodyComp = if (latest.hasBodyComp) latest else it.sessionBodyComp,
                    )
                }
                BleLogger.i(
                    "Restored last measurement ${latest.weight} kg " +
                        "(${latest.dateTime})",
                )
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

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setCrashReportingEnabled(enabled)
            CrashReporter.setReportingEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled) {
                HeartbeatScheduler.schedule(app)
                val today = LocalDate.now(ZoneOffset.UTC).toString()
                if (preferences.lastHeartbeatUtcDay() != today) {
                    val info = HeartbeatInfo(
                        channel = if (BuildConfig.IS_FDROID) "fdroid" else "github",
                        isDebugMode = debugMode.value,
                    )
                    if (CrashReporter.sendHeartbeat(info, HeartbeatKind.DAILY)) {
                        preferences.markHeartbeatSent(today)
                    }
                }
            } else {
                HeartbeatScheduler.cancel(app)
            }
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoCheckUpdates(enabled) }
    }

    fun setDeveloperModeUnlocked(unlocked: Boolean) {
        viewModelScope.launch { preferences.setDeveloperModeUnlocked(unlocked) }
    }

    fun setReadingAnimationChoice(choice: String) {
        viewModelScope.launch { preferences.setReadingAnimationChoice(choice) }
    }

    fun setForceShowLoadingAnimations(enabled: Boolean) {
        viewModelScope.launch { preferences.setForceShowLoadingAnimations(enabled) }
    }

    fun setShareToFitBuddy(enabled: Boolean) {
        viewModelScope.launch { preferences.setShareToFitBuddy(enabled) }
    }

    fun refreshFitBuddyAvailability() {
        viewModelScope.launch {
            val available = withContext(Dispatchers.IO) {
                FitBuddyBridge.isAvailable(getApplication())
            }
            _fitBuddyState.update { it.copy(available = available) }
        }
    }

    fun setFitBuddyAvailable(available: Boolean) {
        _fitBuddyState.update { it.copy(available = available) }
    }

    fun dismissFitBuddyMessage() {
        _fitBuddyState.update { it.copy(message = null, isError = false) }
    }

    /** Manually push one reading's overlapping fields to FitBuddy. */
    fun shareToFitBuddy(measurement: ScaleMeasurement) {
        if (_fitBuddyState.value.busy) return
        viewModelScope.launch {
            _fitBuddyState.update { it.copy(busy = true, message = null, isError = false) }
            val result = withContext(Dispatchers.IO) {
                FitBuddyBridge.upsert(getApplication(), measurement)
            }
            result.fold(
                onSuccess = {
                    val whenStr = measurement.dateTime?.let {
                        java.text.SimpleDateFormat("MMM d · HH:mm", java.util.Locale.getDefault())
                            .format(it)
                    } ?: "this reading"
                    _fitBuddyState.update {
                        it.copy(
                            busy = false,
                            available = true,
                            message = "Saved ${String.format(java.util.Locale.US, "%.2f", measurement.weight)} kg " +
                                "($whenStr) as a FitBuddy body measurement.",
                            isError = false,
                        )
                    }
                    BleLogger.i("FitBuddy share ok weight=${measurement.weight}")
                },
                onFailure = { e ->
                    _fitBuddyState.update {
                        it.copy(
                            busy = false,
                            available = FitBuddyBridge.isAvailable(getApplication()),
                            message = e.message ?: "Something went wrong while talking to FitBuddy.",
                            isError = true,
                        )
                    }
                    BleLogger.e("FitBuddy share failed", e)
                },
            )
        }
    }

    /** Pull overlapping body-comp readings from FitBuddy into FreeScale. */
    fun restoreFromFitBuddy(mode: BackupImportMode) {
        if (_fitBuddyState.value.busy) return
        viewModelScope.launch {
            _fitBuddyState.update { it.copy(busy = true, message = null, isError = false) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val rows = FitBuddyBridge.exportAll(getApplication()).getOrThrow()
                    val imported = when (mode) {
                        BackupImportMode.Replace -> {
                            measurements.deleteAll()
                            measurements.insertAll(rows)
                            rows.size
                        }
                        BackupImportMode.Merge -> {
                            val existing = measurements.allRecordedAtEpochMs()
                            val fresh = rows.filter { m ->
                                val at = m.dateTime?.time ?: return@filter true
                                at !in existing
                            }
                            measurements.insertAll(fresh)
                            fresh.size
                        }
                    }
                    val latest = measurements.latest()
                    Triple(imported, rows.size, latest)
                }
            }
            result.fold(
                onSuccess = { (imported, total, latest) ->
                    if (latest != null) {
                        _ui.update {
                            it.copy(
                                lastMeasurement = latest,
                                sessionBodyComp = if (latest.hasBodyComp) {
                                    latest
                                } else {
                                    it.sessionBodyComp
                                },
                            )
                        }
                    }
                    _fitBuddyState.update {
                        it.copy(
                            busy = false,
                            available = true,
                            message = when (mode) {
                                BackupImportMode.Replace ->
                                    "Restored $imported reading${if (imported == 1) "" else "s"} from FitBuddy"
                                BackupImportMode.Merge ->
                                    "Merged $imported of $total from FitBuddy"
                            },
                            isError = false,
                        )
                    }
                    BleLogger.i("FitBuddy restore ok imported=$imported total=$total mode=$mode")
                },
                onFailure = { e ->
                    _fitBuddyState.update {
                        it.copy(
                            busy = false,
                            available = FitBuddyBridge.isAvailable(getApplication()),
                            message = e.message ?: "Restore from FitBuddy failed",
                            isError = true,
                        )
                    }
                    BleLogger.e("FitBuddy restore failed", e)
                },
            )
        }
    }

    fun setProgressPeriodUnit(unit: PeriodUnit) {
        _progress.update { it.copy(periodUnit = unit, periodAnchor = LocalDate.now()) }
    }

    fun setProgressMetric(metric: ProgressMetric) {
        _progress.update { it.copy(metric = metric) }
    }

    fun goToPreviousProgressPeriod() {
        _progress.update { state ->
            val prev = state.period.previous()
            state.copy(periodAnchor = prev.start)
        }
    }

    fun goToNextProgressPeriod() {
        _progress.update { state ->
            if (!state.period.canGoNext()) return@update state
            val next = state.period.next()
            state.copy(periodAnchor = next.start)
        }
    }

    fun dismissBackupMessage() {
        _backupState.update { it.copy(message = null, isError = false) }
    }

    /** Write a FreeScale JSON backup to cache and open the system share sheet. */
    fun exportBackup() {
        if (_backupState.value.busy) return
        viewModelScope.launch {
            _backupState.update { BackupUiState(busy = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val rows = measurements.getAllOldestFirst()
                    val profile = preferences.userProfile.first()
                    val json = MeasurementBackupJson.encode(rows, profile)
                    val dir = java.io.File(app.cacheDir, "share").apply { mkdirs() }
                    val file = java.io.File(
                        dir,
                        "FreeScale-backup-${java.time.LocalDate.now()}.json",
                    )
                    file.writeText(json, Charsets.UTF_8)
                    file to rows.size
                }
            }
            result.fold(
                onSuccess = { (file, count) ->
                    runCatching {
                        BackupShare.shareJsonFile(
                            getApplication(),
                            file,
                            chooserTitle = "Share FreeScale backup",
                        )
                    }.onFailure { e ->
                        _backupState.update {
                            BackupUiState(
                                busy = false,
                                message = e.message ?: "Could not open share sheet",
                                isError = true,
                            )
                        }
                        BleLogger.e("Backup share sheet failed", e)
                        return@fold
                    }
                    // Share sheet owns the rest of the flow; don't leave a sticky status under the buttons.
                    _backupState.update { BackupUiState() }
                    BleLogger.i("Backup export ok ($count readings)")
                },
                onFailure = { e ->
                    _backupState.update {
                        BackupUiState(
                            busy = false,
                            message = e.message ?: "Export failed",
                            isError = true,
                        )
                    }
                    BleLogger.e("Backup export failed", e)
                },
            )
        }
    }

    /** Read a FreeScale JSON backup from [uri] and merge or replace local history. */
    fun importBackup(uri: Uri, mode: BackupImportMode) {
        if (_backupState.value.busy) return
        viewModelScope.launch {
            _backupState.update { BackupUiState(busy = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val json = app.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Could not open backup file")
                    val doc = MeasurementBackupJson.decode(json)
                    val imported = when (mode) {
                        BackupImportMode.Replace -> {
                            measurements.deleteAll()
                            measurements.insertAll(doc.measurements)
                            doc.measurements.size
                        }
                        BackupImportMode.Merge -> {
                            val existing = measurements.allRecordedAtEpochMs()
                            val fresh = doc.measurements.filter { m ->
                                val at = m.dateTime?.time ?: return@filter true
                                at !in existing
                            }
                            measurements.insertAll(fresh)
                            fresh.size
                        }
                    }
                    doc.profile?.let { p ->
                        preferences.setUserProfile(p.heightCm, p.ageYears, p.male)
                    }
                    val latest = measurements.latest()
                    Triple(imported, doc.measurements.size, latest)
                }
            }
            result.fold(
                onSuccess = { (imported, totalInFile, latest) ->
                    if (latest != null) {
                        _ui.update {
                            it.copy(
                                lastMeasurement = latest,
                                sessionBodyComp = if (latest.hasBodyComp) {
                                    latest
                                } else {
                                    it.sessionBodyComp
                                },
                            )
                        }
                    } else if (mode == BackupImportMode.Replace) {
                        _ui.update {
                            it.copy(lastMeasurement = null, sessionBodyComp = null)
                        }
                    }
                    val modeLabel = if (mode == BackupImportMode.Replace) "Replaced" else "Merged"
                    _backupState.update {
                        BackupUiState(
                            busy = false,
                            message = "$modeLabel $imported of $totalInFile reading" +
                                "${if (totalInFile == 1) "" else "s"} from backup",
                            isError = false,
                        )
                    }
                    BleLogger.i("Backup import ok mode=$mode imported=$imported/$totalInFile")
                },
                onFailure = { e ->
                    _backupState.update {
                        BackupUiState(
                            busy = false,
                            message = e.message ?: "Import failed",
                            isError = true,
                        )
                    }
                    BleLogger.e("Backup import failed", e)
                },
            )
        }
    }

    /**
     * Settings “crafted with ♥” double-tap. Sends a confetti heartbeat when crash
     * reporting is on (ignores the once-per-day gate so the tap still pulses).
     */
    fun sendHeartbeatFromLoveTap() {
        viewModelScope.launch {
            val info = HeartbeatInfo(
                channel = if (BuildConfig.IS_FDROID) "fdroid" else "github",
                isDebugMode = debugMode.value,
            )
            val sent = withContext(Dispatchers.IO) {
                CrashReporter.sendHeartbeat(info, HeartbeatKind.CONFETTI)
            }
            if (sent) {
                preferences.markHeartbeatSent(LocalDate.now(ZoneOffset.UTC).toString())
            }
        }
    }

    /** Manual or automatic check; [silent] skips status text for up-to-date / network errors. */
    fun checkForUpdates(currentVersionCode: Int = BuildConfig.VERSION_CODE, silent: Boolean = false) {
        if (BuildConfig.IS_FDROID) return
        if (_updateState.value.isChecking) return
        viewModelScope.launch {
            _updateState.update {
                it.copy(
                    isChecking = true,
                    statusMessage = if (silent) it.statusMessage else null,
                    statusIsError = if (silent) it.statusIsError else false,
                )
            }
            when (val result = updateChecker.checkForUpdate(currentVersionCode)) {
                is UpdateCheckResult.Available -> _updateState.update {
                    it.copy(
                        isChecking = false,
                        updateInfo = result,
                        statusMessage = null,
                        statusIsError = false,
                    )
                }
                UpdateCheckResult.UpToDate -> _updateState.update {
                    it.copy(
                        isChecking = false,
                        updateInfo = null,
                        statusMessage = if (silent) null else "You're on the latest version",
                        statusIsError = false,
                    )
                }
                is UpdateCheckResult.Error -> _updateState.update {
                    it.copy(
                        isChecking = false,
                        updateInfo = null,
                        statusMessage = if (silent) null else result.message,
                        statusIsError = !silent,
                    )
                }
            }
        }
    }

    fun dismissUpdatePrompt() {
        _updateState.update {
            it.copy(
                updateInfo = null,
                statusMessage = null,
                statusIsError = false,
            )
        }
    }

    fun acknowledgeUpdateDownloadStarted() {
        _updateState.update {
            it.copy(
                updateInfo = null,
                statusMessage = "Download started in your browser — install the APK when it finishes",
                statusIsError = false,
            )
        }
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
                val stamped = if (m.dateTime == null) {
                    m.copy(dateTime = Date())
                } else {
                    m
                }
                if (stamped.hasBodyComp) {
                    _ui.update {
                        it.copy(
                            measurement = stamped,
                            lastMeasurement = stamped,
                            pendingWeightOnly = null,
                            sessionBodyComp = stamped,
                            status = "Measurement complete",
                            measurePhase = MeasurePhase.Complete,
                        )
                    }
                    persistMeasurement(stamped)
                } else {
                    // Weight-only: show confirm dialog; do not persist yet.
                    BleLogger.i("Weight-only pending confirm w=${stamped.weight}")
                    _ui.update {
                        it.copy(
                            measurement = stamped,
                            pendingWeightOnly = stamped,
                            status = "Weight only — log this reading?",
                            measurePhase = MeasurePhase.Complete,
                        )
                    }
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

    /** Persist a weight-only reading the user chose to keep. */
    fun confirmWeightOnly() {
        val pending = _ui.value.pendingWeightOnly ?: return
        _ui.update {
            it.copy(
                lastMeasurement = pending,
                pendingWeightOnly = null,
                status = "Measurement complete",
            )
        }
        persistMeasurement(pending)
    }

    /** Drop a weight-only reading without saving. */
    fun discardWeightOnly() {
        val pending = _ui.value.pendingWeightOnly ?: return
        BleLogger.i("Discarded weight-only w=${pending.weight}")
        _ui.update {
            it.copy(
                measurement = it.lastMeasurement,
                pendingWeightOnly = null,
                status = "Reading discarded",
            )
        }
    }

    private fun persistMeasurement(stamped: ScaleMeasurement) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { measurements.save(stamped) }
            }.onSuccess { id ->
                BleLogger.i("Saved measurement id=$id weight=${stamped.weight}")
                if (stamped.hasBodyComp) {
                    maybeAutoShareToFitBuddy(stamped)
                }
            }.onFailure { t ->
                BleLogger.e("Failed to persist measurement", t)
            }
        }
    }

    /**
     * Auto-push body-comp readings only (never weight-only), at most once per local calendar day.
     */
    private suspend fun maybeAutoShareToFitBuddy(stamped: ScaleMeasurement) {
        if (!stamped.hasBodyComp) {
            BleLogger.i("Skip FitBuddy auto-share; weight-only reading")
            return
        }
        if (!preferences.shareToFitBuddy.first()) return
        val localDate = fitBuddyAutoShareLocalDate(stamped) ?: return
        if (preferences.lastFitBuddyAutoShareLocalDate() == localDate) {
            BleLogger.i("Skip FitBuddy auto-share; already pushed for $localDate")
            return
        }
        val result = withContext(Dispatchers.IO) {
            FitBuddyBridge.upsert(getApplication(), stamped)
        }
        result.fold(
            onSuccess = {
                preferences.markFitBuddyAutoShareLocalDate(localDate)
                val weight = String.format(java.util.Locale.US, "%.2f", stamped.weight)
                Toast.makeText(
                    getApplication(),
                    "Synced to FitBuddy · $weight kg",
                    Toast.LENGTH_SHORT,
                ).show()
                BleLogger.i("Auto-shared to FitBuddy weight=${stamped.weight} date=$localDate")
            },
            onFailure = { e ->
                BleLogger.e("Auto-share to FitBuddy failed", e)
                _fitBuddyState.update {
                    it.copy(
                        available = FitBuddyBridge.isAvailable(getApplication()),
                        message = e.message ?: "Auto-share to FitBuddy failed",
                        isError = true,
                    )
                }
            },
        )
    }

    /** Calendar day of the reading in local time (`yyyy-MM-dd`), used to cap auto-share to once/day. */
    private fun fitBuddyAutoShareLocalDate(m: ScaleMeasurement): String? {
        val at = m.dateTime ?: return null
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(at)
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
