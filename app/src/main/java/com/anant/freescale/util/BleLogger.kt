package com.anant.freescale.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central BLE/debug logger: Logcat tag FreeScale/BLE, in-memory ring for UI, optional session file.
 */
object BleLogger {
    const val TAG = "FreeScale/BLE"
    private const val MAX_LINES = 500

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Volatile
    private var sessionFile: File? = null

    fun startSession(context: Context) {
        val dir = File(context.filesDir, "ble_logs").apply { mkdirs() }
        val name = "session_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".log"
        sessionFile = File(dir, name)
        i("Session file: ${sessionFile?.absolutePath}")
        i("adb pull ${sessionFile?.absolutePath}")
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun d(msg: String) = append("D", msg)
    fun i(msg: String) = append("I", msg)
    fun w(msg: String) = append("W", msg)
    fun e(msg: String, t: Throwable? = null) {
        append("E", if (t != null) "$msg | ${t.message}" else msg)
        if (t != null) Log.e(TAG, msg, t)
    }

    fun hex(label: String, data: ByteArray) {
        i("$label (${data.size}b) ${data.toHex()}")
    }

    private fun append(level: String, msg: String) {
        val line = "${timeFmt.format(Date())} $level $msg"
        when (level) {
            "E" -> Log.e(TAG, msg)
            "W" -> Log.w(TAG, msg)
            "I" -> Log.i(TAG, msg)
            else -> Log.d(TAG, msg)
        }
        _lines.update { cur ->
            val next = cur + line
            if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
        }
        try {
            sessionFile?.appendText(line + "\n")
        } catch (_: Exception) {
            // ignore IO errors while logging
        }
    }
}

fun ByteArray.toHex(): String =
    joinToString(" ") { String.format("%02X", it) }
