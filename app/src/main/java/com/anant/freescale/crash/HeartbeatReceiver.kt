package com.anant.freescale.crash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anant.freescale.BuildConfig
import com.anant.freescale.FreeScaleApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * UTC-midnight (inexact) heartbeat. No-op when crash reporting is off.
 */
class HeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HEARTBEAT) return
        val app = context.applicationContext as? FreeScaleApp ?: return

        runBlocking {
            val prefs = app.preferences
            val enabled = prefs.crashReportingEnabled.first()
            if (!enabled) {
                HeartbeatScheduler.cancel(context)
                return@runBlocking
            }
            val today = LocalDate.now(ZoneOffset.UTC).toString()
            if (prefs.lastHeartbeatUtcDay() == today) {
                HeartbeatScheduler.schedule(context)
                return@runBlocking
            }
            val debugMode = prefs.debugMode.first()
            val info = HeartbeatInfo(
                channel = if (BuildConfig.IS_FDROID) "fdroid" else "github",
                isDebugMode = debugMode,
            )
            if (CrashReporter.sendHeartbeat(info, HeartbeatKind.DAILY)) {
                prefs.markHeartbeatSent(today)
            }
            HeartbeatScheduler.schedule(context)
        }
    }

    companion object {
        const val ACTION_HEARTBEAT = "com.anant.freescale.action.SENTRY_HEARTBEAT"
    }
}
