package com.chalo.driver.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chalo.driver.ChaloDriverApplication
import com.chalo.driver.MainActivity
import com.chalo.driver.R
import com.chalo.driver.domain.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class DriverFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed")
        serviceScope.launch {
            try {
                authRepository.registerDeviceToken(token)
                Timber.d("Device token registered with backend")
            } catch (e: Exception) {
                Timber.w(e, "Failed to register device token")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM message received: ${message.data}")

        val data = message.data
        when (data["type"]) {
            "RIDE_OFFER" -> handleRideOffer(data)
            "RIDE_CANCELLED" -> handleRideCancelled(data)
            else -> {
                // Generic notification fallback
                message.notification?.let { showGenericNotification(it) }
            }
        }
    }

    private fun handleRideOffer(data: Map<String, String>) {
        val rideId = data["rideId"] ?: return
        val pickupAddress = data["pickupAddress"] ?: "Pickup"
        val fare = data["fare"] ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "incoming_ride")
            putExtra("ride_id", rideId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, rideId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, ChaloDriverApplication.CHANNEL_RIDE_REQUESTS)
            .setContentTitle("New Ride Request!")
            .setContentText("Pickup: $pickupAddress${if (fare.isNotEmpty()) " • ₹$fare" else ""}")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        try {
            NotificationManagerCompat.from(this).notify(RIDE_OFFER_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }

    private fun handleRideCancelled(data: Map<String, String>) {
        // Dismiss any active ride offer notification
        NotificationManagerCompat.from(this).cancel(RIDE_OFFER_NOTIFICATION_ID)

        val notification = NotificationCompat.Builder(this, ChaloDriverApplication.CHANNEL_RIDE_UPDATES)
            .setContentTitle("Ride Cancelled")
            .setContentText(data["message"] ?: "The ride has been cancelled by the customer.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(RIDE_CANCELLED_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }

    private fun showGenericNotification(notification: RemoteMessage.Notification) {
        val built = NotificationCompat.Builder(this, ChaloDriverApplication.CHANNEL_RIDE_UPDATES)
            .setContentTitle(notification.title ?: "Chalo Driver")
            .setContentText(notification.body ?: "")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(GENERIC_NOTIFICATION_ID, built)
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val RIDE_OFFER_NOTIFICATION_ID = 2001
        private const val RIDE_CANCELLED_NOTIFICATION_ID = 2002
        private const val GENERIC_NOTIFICATION_ID = 2099
    }
}
