package com.anant.freescale

import android.app.Application
import com.anant.freescale.crash.CrashReporter
import com.anant.freescale.crash.HeartbeatInfo
import com.anant.freescale.crash.HeartbeatKind
import com.anant.freescale.crash.HeartbeatScheduler
import com.anant.freescale.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneOffset

class FreeScaleApp : Application() {
    val preferences by lazy { AppPreferences(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val (reporting, supportId) = runBlocking {
            val id = preferences.ensureSupportId()
            val enabled = preferences.crashReportingEnabled.first()
            enabled to id
        }

        CrashReporter.init(
            app = this,
            enabled = reporting,
            supportId = supportId,
        )

        if (reporting) {
            HeartbeatScheduler.schedule(this)
            appScope.launch { maybeSendDailyHeartbeat() }
        } else {
            HeartbeatScheduler.cancel(this)
        }
    }

    suspend fun maybeSendDailyHeartbeat() {
        if (!preferences.crashReportingEnabled.first()) return
        val today = LocalDate.now(ZoneOffset.UTC).toString()
        if (preferences.lastHeartbeatUtcDay() == today) return
        val info = HeartbeatInfo(
            channel = if (BuildConfig.IS_FDROID) "fdroid" else "github",
            isDebugMode = preferences.debugMode.first(),
        )
        if (CrashReporter.sendHeartbeat(info, HeartbeatKind.DAILY)) {
            preferences.markHeartbeatSent(today)
        }
    }
}
