package com.chalo.customer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chalo.customer.domain.repository.FeatureFlagRepository
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ChaloApplication : Application() {

    @Inject lateinit var featureFlags: FeatureFlagRepository

    // App-scoped coroutine scope for fire-and-forget startup work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Timber — debug logging only (stripped in release by ProGuard)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Google Places SDK — must be initialised before any Place API call
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        // Fetch Remote Config flags in the background — defaults stay active until fetch completes
        appScope.launch { featureFlags.fetchAndActivate() }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Main ride notifications channel
            val ridesChannel = NotificationChannel(
                CHANNEL_RIDES,
                "Ride Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about your ride status"
                enableVibration(true)
            }

            // Promotions channel
            val promoChannel = NotificationChannel(
                CHANNEL_PROMO,
                "Offers & Promotions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Special offers and promotions"
            }

            manager.createNotificationChannels(listOf(ridesChannel, promoChannel))
        }
    }

    companion object {
        const val CHANNEL_RIDES = "chalo_rides"
        const val CHANNEL_PROMO = "chalo_promo"
    }
}
