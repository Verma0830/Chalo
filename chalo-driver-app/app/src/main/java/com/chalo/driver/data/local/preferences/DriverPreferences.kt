package com.chalo.driver.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chalo_driver_prefs")

@Singleton
class DriverPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    private val keyUserId   = stringPreferencesKey("user_id")
    private val keyUserName = stringPreferencesKey("user_name")
    private val keyUserPhone = stringPreferencesKey("user_phone")
    private val keyVerificationStatus = stringPreferencesKey("verification_status")
    private val keyIsOnline = booleanPreferencesKey("is_online")

    val userId: Flow<String?>   = store.data.map { it[keyUserId] }
    val userName: Flow<String?> = store.data.map { it[keyUserName] }
    val userPhone: Flow<String?> = store.data.map { it[keyUserPhone] }
    val verificationStatus: Flow<String?> = store.data.map { it[keyVerificationStatus] }
    val isOnline: Flow<Boolean> = store.data.map { it[keyIsOnline] ?: false }

    suspend fun saveUser(userId: String, name: String?, phone: String) {
        store.edit {
            it[keyUserId]   = userId
            it[keyUserName] = name ?: ""
            it[keyUserPhone] = phone
        }
    }

    suspend fun setVerificationStatus(status: String) {
        store.edit { it[keyVerificationStatus] = status }
    }

    suspend fun setOnline(online: Boolean) {
        store.edit { it[keyIsOnline] = online }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
