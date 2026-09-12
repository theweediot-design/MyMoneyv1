package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.bankDataStore: DataStore<Preferences> by preferencesDataStore(name = "mymoney_bank_preferences")

class BankPreferenceManager(private val context: Context) {

    companion object {
        val KEY_ACTIVE_BANKS = stringSetPreferencesKey("active_banks_filter")
        val KEY_IS_USER_CONFIGURED = booleanPreferencesKey("is_user_configured")
        val KEY_HIDDEN_ACCOUNT_IDS = stringSetPreferencesKey("hidden_account_ids")
    }

    /**
     * Emits null if the user has never configured bank preferences yet.
     * Emits a Set<String> once the user has customized their active banks.
     */
    val activeBanksFlow: Flow<Set<String>?> = context.bankDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val isConfigured = preferences[KEY_IS_USER_CONFIGURED] ?: false
            if (!isConfigured) {
                null
            } else {
                preferences[KEY_ACTIVE_BANKS] ?: emptySet()
            }
        }

    val isUserConfiguredFlow: Flow<Boolean> = context.bankDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_USER_CONFIGURED] ?: false
        }

    val hiddenAccountIdsFlow: Flow<Set<String>> = context.bankDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_HIDDEN_ACCOUNT_IDS] ?: emptySet()
        }

    suspend fun toggleAccountHidden(accountId: Long, currentHidden: Set<String>) {
        val idStr = accountId.toString()
        val updated = currentHidden.toMutableSet()
        if (updated.contains(idStr)) {
            updated.remove(idStr)
        } else {
            updated.add(idStr)
        }
        context.bankDataStore.edit { preferences ->
            preferences[KEY_HIDDEN_ACCOUNT_IDS] = updated
        }
    }

    suspend fun setAccountHidden(accountId: Long, isHidden: Boolean, currentHidden: Set<String>) {
        val idStr = accountId.toString()
        val updated = currentHidden.toMutableSet()
        if (isHidden) {
            updated.add(idStr)
        } else {
            updated.remove(idStr)
        }
        context.bankDataStore.edit { preferences ->
            preferences[KEY_HIDDEN_ACCOUNT_IDS] = updated
        }
    }

    suspend fun saveActiveBanks(banks: Set<String>) {
        context.bankDataStore.edit { preferences ->
            preferences[KEY_ACTIVE_BANKS] = banks
            preferences[KEY_IS_USER_CONFIGURED] = true
        }
    }

    suspend fun toggleBank(bankCode: String, currentActive: Set<String>) {
        val updated = currentActive.toMutableSet()
        if (updated.contains(bankCode)) {
            updated.remove(bankCode)
        } else {
            updated.add(bankCode)
        }
        saveActiveBanks(updated)
    }

    suspend fun selectAll(banks: Collection<String>) {
        saveActiveBanks(banks.toSet())
    }

    suspend fun clearAll() {
        saveActiveBanks(emptySet())
    }

    suspend fun resetToDefault() {
        context.bankDataStore.edit { preferences ->
            preferences.remove(KEY_ACTIVE_BANKS)
            preferences.remove(KEY_IS_USER_CONFIGURED)
        }
    }
}
