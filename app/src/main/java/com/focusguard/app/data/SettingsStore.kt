package com.focusguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusguard.app.data.db.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small key/value settings stored on the device (Jetpack DataStore). */
class SettingsStore(private val context: Context) {

    private object Keys {
        /** Random ID created on first launch. In v2 it is linked to the user's account. */
        val INSTALL_ID = stringPreferencesKey("install_id")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        /** User agreed to the accessibility "prominent disclosure" (Play Store requirement). */
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val FOCUS_DEFAULT_MINUTES = intPreferencesKey("focus_default_minutes")
    }

    private val data = context.dataStore.data

    val installId: Flow<String?> = data.map { it[Keys.INSTALL_ID] }
    val onboardingDone: Flow<Boolean> = data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val disclosureAccepted: Flow<Boolean> = data.map { it[Keys.DISCLOSURE_ACCEPTED] ?: false }
    val strictMode: Flow<Boolean> = data.map { it[Keys.STRICT_MODE] ?: false }
    val focusDefaultMinutes: Flow<Int> = data.map { it[Keys.FOCUS_DEFAULT_MINUTES] ?: 25 }

    /** Creates the anonymous install ID once; later calls return the same value. */
    suspend fun ensureInstallId(): String {
        data.first()[Keys.INSTALL_ID]?.let { return it }
        var id = ""
        context.dataStore.edit { prefs ->
            id = prefs[Keys.INSTALL_ID] ?: newId().also { prefs[Keys.INSTALL_ID] = it }
        }
        return id
    }

    suspend fun setOnboardingDone(done: Boolean) =
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }

    suspend fun setDisclosureAccepted(accepted: Boolean) =
        context.dataStore.edit { it[Keys.DISCLOSURE_ACCEPTED] = accepted }

    suspend fun setStrictMode(enabled: Boolean) =
        context.dataStore.edit { it[Keys.STRICT_MODE] = enabled }

    suspend fun setFocusDefaultMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.FOCUS_DEFAULT_MINUTES] = minutes }
}
