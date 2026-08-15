package com.anant.freescale.crash

import android.app.Application
import android.os.Build
import android.util.Log
import com.anant.freescale.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.MonitorConfig
import io.sentry.MonitorSchedule
import io.sentry.MonitorScheduleUnit
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.SentryOptions.BeforeSendCallback
import io.sentry.android.core.SentryAndroid
import io.sentry.logger.SentryLogParameters
import io.sentry.metrics.SentryMetricsParameters
import io.sentry.protocol.SentryId
import io.sentry.protocol.User
import java.util.concurrent.atomic.AtomicBoolean

/** Coarse snapshot attached to daily heartbeats (no PII beyond Support ID as Sentry user). */
data class HeartbeatInfo(
    val channel: String,
    val isDebugMode: Boolean = false,
    val androidSdk: Int = Build.VERSION.SDK_INT,
    val manufacturer: String = Build.MANUFACTURER.orEmpty().take(64),
    val model: String = Build.MODEL.orEmpty().take(64),
)

enum class HeartbeatKind {
    DAILY,
    UPDATE,
    CONFETTI;

    val logMessage: String
        get() = when (this) {
            DAILY -> "FreeScale daily heartbeat"
            UPDATE -> "FreeScale update heartbeat"
            CONFETTI -> "FreeScale confetti heartbeat"
        }
}

/**
 * Thin Sentry wrapper: crashes/ANRs and optional daily heartbeats.
 * Heartbeats are gated by [reportingEnabled] (same opt-out as crash reports).
 * Empty [BuildConfig.SENTRY_DSN_BLOB] keeps the SDK uninitialized.
 */
object CrashReporter {

    const val HEARTBEAT_MONITOR_SLUG = "freescale-daily-heartbeat"

    private val ready = AtomicBoolean(false)

    @Volatile
    private var reportingEnabled: Boolean = true

    fun init(app: Application, enabled: Boolean, supportId: String) {
        if (BuildConfig.SENTRY_DSN_BLOB.isBlank()) return
        val dsn = DsnVault.decode(BuildConfig.SENTRY_DSN_BLOB, BuildConfig.SENTRY_DSN_MASK).trim()
        if (dsn.isEmpty()) return
        reportingEnabled = enabled
        SentryAndroid.init(app) { options ->
            options.dsn = dsn
            options.isSendDefaultPii = false
            options.tracesSampleRate = 0.0
            options.isEnableUserInteractionTracing = false
            options.isEnableAutoSessionTracking = false
            options.isSendClientReports = false
            options.logs.isEnabled = true
            options.metrics.isEnabled = true
            options.environment = when {
                BuildConfig.DEBUG -> "debug"
                BuildConfig.IS_FDROID -> "fdroid"
                else -> "github"
            }
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.setBeforeSend(BeforeSendCallback { event, _ ->
                if (!reportingEnabled) return@BeforeSendCallback null
                val msg = event.message?.formatted
                if (event.fingerprints?.contains(HEARTBEAT_MONITOR_SLUG) == true ||
                    HeartbeatKind.entries.any { it.logMessage == msg }
                ) {
                    return@BeforeSendCallback null
                }
                scrub(event)
            })
        }
        if (supportId.isNotBlank()) {
            Sentry.setUser(User().apply { id = supportId })
        }
        ready.set(true)
    }

    fun setReportingEnabled(enabled: Boolean) {
        reportingEnabled = enabled
    }

    fun isReportingEnabled(): Boolean = reportingEnabled

    fun setSupportId(supportId: String) {
        if (!ready.get() || supportId.isBlank()) return
        Sentry.setUser(User().apply { id = supportId })
    }

    fun breadcrumb(category: String, message: String) {
        if (!ready.get() || !reportingEnabled) return
        Sentry.addBreadcrumb(
            Breadcrumb().apply {
                this.category = category
                this.message = message
                level = SentryLevel.INFO
            },
        )
    }

    /**
     * Anonymous heartbeat (Crons + Metrics/Logs). No-op when crash reporting is off.
     */
    fun sendHeartbeat(info: HeartbeatInfo, kind: HeartbeatKind = HeartbeatKind.DAILY): Boolean {
        if (!ready.get() || !reportingEnabled) return false
        return runCatching {
            val checkIn = CheckIn(HEARTBEAT_MONITOR_SLUG, CheckInStatus.OK).apply {
                release =
                    "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                environment = when {
                    BuildConfig.DEBUG -> "debug"
                    BuildConfig.IS_FDROID -> "fdroid"
                    else -> "github"
                }
                duration = 0.0
                monitorConfig = MonitorConfig(
                    MonitorSchedule.interval(1, MonitorScheduleUnit.DAY),
                ).apply {
                    checkinMargin = 2L * 24L * 60L
                    maxRuntime = 5L
                    timezone = "UTC"
                    failureIssueThreshold = 10L
                }
            }
            val checkInId = Sentry.captureCheckIn(checkIn)
            emitFleetPulse(info, message = kind.logMessage)
            Sentry.flush(5_000L)
            checkInId != SentryId.EMPTY_ID
        }.onFailure { e ->
            Log.e(TAG, "heartbeat failed", e)
        }.getOrDefault(false)
    }

    private fun emitFleetPulse(info: HeartbeatInfo, message: String) {
        val attrList = buildList {
            add(SentryAttribute.stringAttribute("heartbeat", "true"))
            add(SentryAttribute.stringAttribute("channel", info.channel))
            add(SentryAttribute.stringAttribute("manufacturer", info.manufacturer))
            add(SentryAttribute.stringAttribute("model", info.model))
            add(SentryAttribute.integerAttribute("android_sdk", info.androidSdk))
            add(SentryAttribute.stringAttribute("app_version", BuildConfig.VERSION_NAME))
            add(SentryAttribute.stringAttribute("app_build", BuildConfig.VERSION_CODE.toString()))
            add(SentryAttribute.stringAttribute("app_id", BuildConfig.APPLICATION_ID))
            add(SentryAttribute.booleanAttribute("is_debug_mode", info.isDebugMode))
            add(SentryAttribute.booleanAttribute("is_fdroid", BuildConfig.IS_FDROID))
        }
        val attrs = SentryAttributes.of(*attrList.toTypedArray())
        Sentry.metrics().count(
            "freescale.daily_active",
            1.0,
            null,
            SentryMetricsParameters.create(attrs),
        )
        Sentry.logger().log(
            SentryLogLevel.INFO,
            SentryLogParameters.create(attrs),
            message,
        )
    }

    private const val TAG = "FreeScaleCrash"

    private fun scrub(event: SentryEvent): SentryEvent {
        event.request = null
        event.user?.apply {
            email = null
            username = null
            ipAddress = null
        }
        event.extras?.keys?.toList()?.forEach { key ->
            val value = event.extras?.get(key)?.toString().orEmpty()
            if (looksSecret(value) || looksSecret(key)) {
                event.extras?.remove(key)
            }
        }
        return event
    }

    private fun looksSecret(value: String): Boolean {
        if (value.length < 8) return false
        val lower = value.lowercase()
        return lower.contains("sk-") ||
            lower.contains("aiza") ||
            lower.contains("bearer ") ||
            lower.contains("api_key") ||
            lower.contains("apikey") ||
            Regex("eyJ[A-Za-z0-9_-]{20,}").containsMatchIn(value)
    }
}
