package com.anant.freescale.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.anant.freescale.data.db.MeasurementRepository
import kotlinx.coroutines.runBlocking

/**
 * Same-device bridge for FitBuddy: export FreeScale readings via [call].
 *
 * Authority: `${applicationId}.bridge.measurements`
 * Allowed callers: `com.anant.fitbuddy` and `com.anant.fitbuddy.debug`.
 */
class MeasurementBridgeProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return try {
            assertCallerAllowed()
            when (method) {
                METHOD_PING -> Bundle().apply { putBoolean(KEY_OK, true) }
                METHOD_EXPORT_LATEST -> exportLatest()
                METHOD_EXPORT_ALL -> exportAll()
                else -> errorBundle("Unknown method: $method")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "call($method) failed: ${t.message}")
            errorBundle(t.message ?: "Bridge call failed")
        }
    }

    private fun exportLatest(): Bundle {
        return try {
            val ctx = context ?: return errorBundle("App not ready")
            val latest = runBlocking {
                MeasurementRepository(ctx).latest()
            } ?: return errorBundle("No readings in FreeScale yet")
            Bundle().apply {
                putBoolean(KEY_OK, true)
                putString(KEY_JSON, ScaleBridgeJson.encode(latest))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "exportLatest failed", t)
            errorBundle(t.message ?: "Export failed")
        }
    }

    private fun exportAll(): Bundle {
        return try {
            val ctx = context ?: return errorBundle("App not ready")
            val rows = runBlocking {
                MeasurementRepository(ctx).getAllOldestFirst()
            }
            Bundle().apply {
                putBoolean(KEY_OK, true)
                putString(KEY_JSON, ScaleBridgeJson.encodeArray(rows))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "exportAll failed", t)
            errorBundle(t.message ?: "Export failed")
        }
    }

    private fun assertCallerAllowed() {
        val pkg = callingPackage
            ?: error("Missing calling package")
        if (pkg !in ALLOWED_PACKAGES) {
            Log.w(TAG, "reject caller=$pkg allowed=$ALLOWED_PACKAGES")
            error("Caller not allowed: $pkg")
        }
        Log.i(TAG, "allow caller=$pkg")
    }

    private fun errorBundle(message: String): Bundle =
        Bundle().apply {
            putBoolean(KEY_OK, false)
            putString(KEY_ERROR, message)
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "FreeScale/Bridge"

        const val METHOD_PING = "ping"
        const val METHOD_EXPORT_LATEST = "exportLatest"
        const val METHOD_EXPORT_ALL = "exportAll"

        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        const val KEY_JSON = "json"

        val ALLOWED_PACKAGES = setOf(
            "com.anant.fitbuddy",
            "com.anant.fitbuddy.debug",
        )
    }
}
