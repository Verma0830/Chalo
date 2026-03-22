package com.chalo.customer.domain.repository

/**
 * Feature flags loaded from Firebase Remote Config.
 * Keys mirror the platform_config keys seeded in the backend DB.
 */
interface FeatureFlagRepository {
    /** Fetch + activate latest Remote Config values. Call once at app start. */
    suspend fun fetchAndActivate()

    val enableDynamicSurge: Boolean
    val enableWallet: Boolean
    val enablePlacesAutocomplete: Boolean
}
