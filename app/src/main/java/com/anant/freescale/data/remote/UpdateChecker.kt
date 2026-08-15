package com.anant.freescale.data.remote

import com.anant.freescale.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/** Result of comparing the latest GitHub release against the running build. */
sealed interface UpdateCheckResult {
    data class Available(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
        val releaseNotes: String,
        val htmlUrl: String,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Sideload update check via GitHub Releases. Prefers CI tags (`v*-buildN`) so F-Droid
 * clean tags never become the in-app update target.
 */
class UpdateChecker(
    private val client: OkHttpClient = defaultClient,
) {
    private val buildNumberRegex = Regex("build(\\d+)$")

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "FreeScale/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateCheckResult.Error(
                            "GitHub returned HTTP ${response.code}",
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext UpdateCheckResult.Error("Empty response from GitHub")
                    }
                    parseLatestCiRelease(body, currentVersionCode)
                }
            } catch (e: Exception) {
                UpdateCheckResult.Error(e.message ?: "Update check failed")
            }
        }

    private fun parseLatestCiRelease(json: String, currentVersionCode: Int): UpdateCheckResult {
        val releases = JSONArray(json)
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft", false)) continue
            val tagName = release.optString("tag_name")
            if (!buildNumberRegex.containsMatchIn(tagName)) continue

            val assets = release.optJSONArray("assets") ?: continue
            var apkUrl: String? = null
            var preferredUrl: String? = null
            for (a in 0 until assets.length()) {
                val asset = assets.getJSONObject(a)
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (url.isBlank()) continue
                if (name.equals("FreeScale-latest.apk", ignoreCase = true)) {
                    preferredUrl = url
                } else if (apkUrl == null) {
                    apkUrl = url
                }
            }
            val downloadUrl = preferredUrl ?: apkUrl ?: continue

            val remoteVersionCode = buildNumberRegex.find(tagName)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: return UpdateCheckResult.Error("Could not read version from latest release")

            if (remoteVersionCode <= currentVersionCode) {
                return UpdateCheckResult.UpToDate
            }

            return UpdateCheckResult.Available(
                versionName = tagName
                    .removePrefix("v")
                    .substringBefore("-build")
                    .ifBlank { release.optString("name") },
                versionCode = remoteVersionCode,
                downloadUrl = downloadUrl,
                releaseNotes = release.optString("body").orEmpty(),
                htmlUrl = release.optString("html_url"),
            )
        }
        return UpdateCheckResult.Error("No GitHub CI release with an APK found")
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/anantdark/FreeScale/releases?per_page=30"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
