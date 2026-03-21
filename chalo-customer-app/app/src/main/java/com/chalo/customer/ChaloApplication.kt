package com.chalo.customer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ChaloApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber — debug logging only (stripped in release by ProGuard)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

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
