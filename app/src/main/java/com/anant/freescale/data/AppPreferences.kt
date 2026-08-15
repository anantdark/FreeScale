package com.anant.freescale.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    suspend fun setUserProfile(heightCm: String, ageYears: String, male: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HEIGHT_CM] = heightCm
            prefs[KEY_AGE_YEARS] = ageYears
            prefs[KEY_MALE] = male
        }
    }

    companion object {
        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        /** Migrated from earlier "developer_mode" preference. */
        private val KEY_DEBUG_MODE_LEGACY = booleanPreferencesKey("developer_mode")
        private val KEY_MATERIAL_YOU = booleanPreferencesKey("material_you")
        private val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        private val KEY_REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        private val KEY_HEIGHT_CM = stringPreferencesKey("height_cm")
        private val KEY_AGE_YEARS = stringPreferencesKey("age_years")
        private val KEY_MALE = booleanPreferencesKey("male")
    }
}
