package com.anant.freescale.crash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import java.util.TimeZone

/** Inexact UTC-midnight alarm for the daily Sentry heartbeat. */
object HeartbeatScheduler {

    private const val TAG = "HeartbeatScheduler"
    private const val REQUEST_CODE = 8201

    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextMidnightUtcMillis()
        val pending = pendingIntent(app)
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }.onFailure { e ->
            Log.e(TAG, "Failed to schedule heartbeat alarm", e)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(app))
    }

    private fun nextMidnightUtcMillis(): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HeartbeatReceiver::class.java).apply {
            action = HeartbeatReceiver.ACTION_HEARTBEAT
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
