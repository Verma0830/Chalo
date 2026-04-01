package com.chalo.driver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ChaloDriverApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val rideRequestChannel = NotificationChannel(
                CHANNEL_RIDE_REQUESTS,
                "Ride Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming ride request notifications"
                enableVibration(true)
            }

            val rideUpdatesChannel = NotificationChannel(
                CHANNEL_RIDE_UPDATES,
                "Ride Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Ride status updates"
            }

            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing location tracking notification"
            }

            manager.createNotificationChannels(
                listOf(rideRequestChannel, rideUpdatesChannel, locationChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_RIDE_REQUESTS = "chalo_driver_rides"
        const val CHANNEL_RIDE_UPDATES = "chalo_driver_updates"
        const val CHANNEL_LOCATION = "chalo_driver_location"
    }
}
