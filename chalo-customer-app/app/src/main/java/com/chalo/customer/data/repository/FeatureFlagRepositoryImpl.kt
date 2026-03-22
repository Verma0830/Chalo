package com.chalo.customer.data.repository

import com.chalo.customer.domain.repository.FeatureFlagRepository
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagRepositoryImpl @Inject constructor() : FeatureFlagRepository {

    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig.also { rc ->
        rc.setConfigSettingsAsync(
            remoteConfigSettings {
                // In debug builds, allow fetching every 60 s instead of the 12-hour default
                minimumFetchIntervalInSeconds = if (com.chalo.customer.BuildConfig.DEBUG) 60L else 3600L
            }
        )
        rc.setDefaultsAsync(
            mapOf(
                KEY_DYNAMIC_SURGE        to true,
                KEY_WALLET               to false,
                KEY_PLACES_AUTOCOMPLETE  to true,
            )
        )
    }

    override suspend fun fetchAndActivate() {
        try {
            remoteConfig.fetchAndActivate().await()
            Timber.d("Remote Config fetched and activated")
        } catch (e: Exception) {
            // Non-fatal — defaults stay active
            Timber.w(e, "Remote Config fetch failed, using defaults")
        }
    }

    override val enableDynamicSurge: Boolean
        get() = remoteConfig.getBoolean(KEY_DYNAMIC_SURGE)

    override val enableWallet: Boolean
        get() = remoteConfig.getBoolean(KEY_WALLET)

    override val enablePlacesAutocomplete: Boolean
        get() = remoteConfig.getBoolean(KEY_PLACES_AUTOCOMPLETE)

    companion object {
        private const val KEY_DYNAMIC_SURGE       = "enable_dynamic_surge"
        private const val KEY_WALLET              = "enable_wallet"
        private const val KEY_PLACES_AUTOCOMPLETE = "enable_places_autocomplete"
    }
}
