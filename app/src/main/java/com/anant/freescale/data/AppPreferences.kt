package com.anant.freescale.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anant.freescale.BuildConfig
import com.anant.freescale.ui.loading.LoadingAnimChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "freescale_settings")

data class UserProfilePrefs(
    val heightCm: String = "175",
    val ageYears: String = "26",
    val male: Boolean = true,
)

class AppPreferences(private val context: Context) {
    val debugMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEBUG_MODE] ?: prefs[KEY_DEBUG_MODE_LEGACY] ?: false
    }

    /** Material You (dynamic color). Default on. */
    val materialYou: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MATERIAL_YOU] ?: true
    }

    /** Scan and link on app open. Default off. */
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CONNECT] ?: false
    }

    /** Drop non-critical motion on Home. Default off. */
    val reduceAnimations: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_REDUCE_ANIMATIONS] ?: false
    }

    /**
     * Crash reports + heartbeats. Default: on for GitHub release builds,
     * off for F-Droid and debug.
     */
    val crashReportingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CRASH_REPORTING] ?: defaultCrashReportingEnabled()
    }

    /**
     * Silent GitHub release check shortly after startup. Default: on for GitHub
     * release builds, off for F-Droid and debug.
     */
    val autoCheckUpdates: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATES] ?: defaultAutoCheckUpdates()
    }

    /** Unlocked via 31 taps on Package in About. */
    val developerModeUnlocked: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVELOPER_UNLOCKED] ?: false
    }

    /** Reading-card wait animation: off / random / animation id. */
    val readingAnimationChoice: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_READING_ANIMATION] ?: LoadingAnimChoice.RANDOM
    }

    /** Keep the measuring banner animation visible on Home for testing. */
    val forceShowLoadingAnimations: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_FORCE_SHOW_LOADING_ANIMS] ?: false
    }

    /**
     * After each successful body-comp save, push overlapping fields to FitBuddy
     * (at most once per calendar day; weight-only saves never auto-share). Default off.
     */
    val shareToFitBuddy: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHARE_TO_FITBUDDY] ?: false
    }

    /** Local `yyyy-MM-dd` of the last successful FitBuddy auto-share, if any. */
    suspend fun lastFitBuddyAutoShareLocalDate(): String? =
        context.dataStore.data.first()[KEY_LAST_FITBUDDY_AUTO_SHARE_DATE]

    suspend fun markFitBuddyAutoShareLocalDate(localDate: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_FITBUDDY_AUTO_SHARE_DATE] = localDate
        }
    }

    val supportId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SUPPORT_ID].orEmpty()
    }

    val userProfile: Flow<UserProfilePrefs> = context.dataStore.data.map { prefs ->
        UserProfilePrefs(
            heightCm = prefs[KEY_HEIGHT_CM] ?: "175",
            ageYears = prefs[KEY_AGE_YEARS] ?: "26",
            male = prefs[KEY_MALE] ?: true,
        )
    }

    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEBUG_MODE] = enabled
            prefs.remove(KEY_DEBUG_MODE_LEGACY)
        }
    }

    suspend fun setMaterialYou(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MATERIAL_YOU] = enabled
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_CONNECT] = enabled
        }
    }

    suspend fun setReduceAnimations(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REDUCE_ANIMATIONS] = enabled
        }
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CRASH_REPORTING] = enabled
        }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_CHECK_UPDATES] = enabled
        }
    }

    suspend fun setDeveloperModeUnlocked(unlocked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVELOPER_UNLOCKED] = unlocked
        }
    }

    suspend fun setReadingAnimationChoice(choice: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_READING_ANIMATION] = choice
        }
    }

    suspend fun setForceShowLoadingAnimations(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FORCE_SHOW_LOADING_ANIMS] = enabled
        }
    }

    suspend fun setShareToFitBuddy(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHARE_TO_FITBUDDY] = enabled
        }
    }

    suspend fun setUserProfile(heightCm: String, ageYears: String, male: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HEIGHT_CM] = heightCm
            prefs[KEY_AGE_YEARS] = ageYears
            prefs[KEY_MALE] = male
        }
    }

    /** Ensures a stable anonymous Support ID exists; returns it. */
    suspend fun ensureSupportId(): String {
        val existing = context.dataStore.data.first()[KEY_SUPPORT_ID].orEmpty()
        if (existing.isNotBlank()) return existing
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[KEY_SUPPORT_ID] = id }
        return id
    }

    suspend fun lastHeartbeatUtcDay(): String? =
        context.dataStore.data.first()[KEY_LAST_HEARTBEAT_DAY]

    suspend fun markHeartbeatSent(utcDay: String) {
        context.dataStore.edit { prefs -> prefs[KEY_LAST_HEARTBEAT_DAY] = utcDay }
    }

    companion object {
        fun defaultCrashReportingEnabled(): Boolean =
            !BuildConfig.DEBUG && !BuildConfig.IS_FDROID

        fun defaultAutoCheckUpdates(): Boolean =
            !BuildConfig.DEBUG && !BuildConfig.IS_FDROID

        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        /** Migrated from earlier "developer_mode" preference. */
        private val KEY_DEBUG_MODE_LEGACY = booleanPreferencesKey("developer_mode")
        private val KEY_MATERIAL_YOU = booleanPreferencesKey("material_you")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        private val KEY_CRASH_REPORTING = booleanPreferencesKey("crash_reporting_enabled")
        private val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        private val KEY_DEVELOPER_UNLOCKED = booleanPreferencesKey("developer_mode_unlocked")
        private val KEY_READING_ANIMATION = stringPreferencesKey("reading_animation_choice")
        private val KEY_FORCE_SHOW_LOADING_ANIMS = booleanPreferencesKey("force_show_loading_anims")
        private val KEY_SHARE_TO_FITBUDDY = booleanPreferencesKey("share_to_fitbuddy")
        private val KEY_LAST_FITBUDDY_AUTO_SHARE_DATE =
            stringPreferencesKey("last_fitbuddy_auto_share_local_date")
        private val KEY_SUPPORT_ID = stringPreferencesKey("support_id")
        private val KEY_LAST_HEARTBEAT_DAY = stringPreferencesKey("sentry_last_heartbeat_utc_day")
        private val KEY_HEIGHT_CM = stringPreferencesKey("height_cm")
        private val KEY_AGE_YEARS = stringPreferencesKey("age_years")
        private val KEY_MALE = booleanPreferencesKey("male")
    }
}
