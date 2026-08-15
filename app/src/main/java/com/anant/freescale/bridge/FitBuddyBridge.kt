package com.anant.freescale.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.anant.freescale.BuildConfig
import com.anant.freescale.data.ScaleMeasurement

/**
 * Client for FitBuddy's [ContentProvider] bridge (`*.bridge.measurements`).
 *
 * Debug FreeScale prefers FitBuddy Dev; release prefers release FitBuddy.
 * Falls back to the other package if the preferred one is missing.
 */
object FitBuddyBridge {
    private const val TAG = "FreeScale/FitBuddy"

    const val METHOD_PING = "ping"
    const val METHOD_UPSERT = "upsert"
    const val METHOD_EXPORT_ALL = "exportAll"

    private val preferredPackages: List<String> =
        if (BuildConfig.DEBUG) {
            listOf("com.anant.fitbuddy.debug", "com.anant.fitbuddy")
        } else {
            listOf("com.anant.fitbuddy", "com.anant.fitbuddy.debug")
        }

    fun isAvailable(context: Context): Boolean =
        resolveAuthority(context) != null

    fun upsert(context: Context, measurement: ScaleMeasurement): Result<Unit> = runCatching {
        val authority = resolveAuthority(context)
            ?: error("FitBuddy is not installed")
        Log.i(TAG, "upsert via $authority")
        val json = ScaleBridgeJson.encode(measurement)
        val extras = Bundle().apply { putString("json", json) }
        val result = context.contentResolver.call(
            Uri.parse("content://$authority"),
            METHOD_UPSERT,
            null,
            extras,
        ) ?: error("FitBuddy did not respond")
        if (!result.getBoolean("ok", false)) {
            error(result.getString("error") ?: "FitBuddy upsert failed")
        }
    }

    fun exportAll(context: Context): Result<List<ScaleMeasurement>> = runCatching {
        val authority = resolveAuthority(context)
            ?: error("FitBuddy is not installed")
        Log.i(TAG, "exportAll via $authority")
        val result = context.contentResolver.call(
            Uri.parse("content://$authority"),
            METHOD_EXPORT_ALL,
            null,
            null,
        ) ?: error("FitBuddy did not respond")
        if (!result.getBoolean("ok", false)) {
            error(result.getString("error") ?: "FitBuddy export failed")
        }
        val json = result.getString("json")
            ?: error("FitBuddy returned empty export")
        ScaleBridgeJson.decodeArray(json)
    }

    private fun resolveAuthority(context: Context): String? {
        val pm = context.packageManager
        Log.i(
            TAG,
            "resolveAuthority DEBUG=${BuildConfig.DEBUG} prefer=$preferredPackages",
        )
        for (pkg in preferredPackages) {
            if (!isPackageInstalled(pm, pkg)) {
                Log.i(TAG, "skip $pkg (not installed / not visible)")
                continue
            }
            val authority = "$pkg.bridge.measurements"
            val ping = runCatching {
                context.contentResolver.call(
                    Uri.parse("content://$authority"),
                    METHOD_PING,
                    null,
                    null,
                )
            }
            ping.onFailure { t ->
                Log.w(TAG, "ping $authority threw", t)
            }
            val bundle = ping.getOrNull()
            if (bundle == null) {
                Log.w(TAG, "ping $authority returned null")
                continue
            }
            val ok = bundle.getBoolean("ok", false)
            if (ok) {
                Log.i(TAG, "using authority $authority")
                return authority
            }
            Log.w(
                TAG,
                "ping $authority ok=false error=${bundle.getString("error")}",
            )
        }
        return null
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
