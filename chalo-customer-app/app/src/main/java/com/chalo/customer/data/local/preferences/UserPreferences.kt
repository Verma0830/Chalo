package com.chalo.customer.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chalo.customer.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chalo_user_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // Keys
    private val keyUserId          = stringPreferencesKey(Constants.PREF_KEY_USER_ID)
    private val keyUserName        = stringPreferencesKey(Constants.PREF_KEY_USER_NAME)
    private val keyUserPhone       = stringPreferencesKey(Constants.PREF_KEY_USER_PHONE)
    private val keyProfileComplete = booleanPreferencesKey(Constants.PREF_KEY_PROFILE_COMPLETE)
    private val keyPendingRatingRideId = stringPreferencesKey(Constants.PREF_KEY_PENDING_RATING_RIDE_ID)
    private val keyPendingRatingShown  = booleanPreferencesKey(Constants.PREF_KEY_PENDING_RATING_SHOWN)
    private val keyPendingRatingTime   = longPreferencesKey(Constants.PREF_KEY_PENDING_RATING_TIME)

    // Flows
    val userId: Flow<String?>          = store.data.map { it[keyUserId] }
    val userName: Flow<String?>        = store.data.map { it[keyUserName] }
    val userPhone: Flow<String?>       = store.data.map { it[keyUserPhone] }
    val isProfileComplete: Flow<Boolean> = store.data.map { it[keyProfileComplete] ?: false }
    val pendingRatingRideId: Flow<String?> = store.data.map { it[keyPendingRatingRideId] }
    val pendingRatingShown: Flow<Boolean>  = store.data.map { it[keyPendingRatingShown] ?: false }
    val pendingRatingTime: Flow<Long?>     = store.data.map { it[keyPendingRatingTime] }

    suspend fun saveUser(userId: String, name: String?, phone: String, profileComplete: Boolean) {
        store.edit {
            it[keyUserId]          = userId
            it[keyUserName]        = name ?: ""
            it[keyUserPhone]       = phone
            it[keyProfileComplete] = profileComplete
        }
    }

    suspend fun updateName(name: String) {
        store.edit { it[keyUserName] = name }
    }

    suspend fun setProfileComplete(complete: Boolean) {
        store.edit { it[keyProfileComplete] = complete }
    }

    suspend fun savePendingRating(rideId: String) {
        store.edit {
            it[keyPendingRatingRideId] = rideId
            it[keyPendingRatingShown]  = false
            it[keyPendingRatingTime]   = System.currentTimeMillis()
        }
    }

    suspend fun markPendingRatingShown() {
        store.edit { it[keyPendingRatingShown] = true }
    }

    suspend fun clearPendingRating() {
        store.edit {
            it.remove(keyPendingRatingRideId)
            it.remove(keyPendingRatingShown)
            it.remove(keyPendingRatingTime)
        }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
