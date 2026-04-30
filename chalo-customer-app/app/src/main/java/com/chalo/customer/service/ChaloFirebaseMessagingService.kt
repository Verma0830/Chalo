package com.chalo.customer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.chalo.customer.MainActivity
import com.chalo.customer.R
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.data.remote.api.AuthApiService
import com.chalo.customer.data.remote.dto.RegisterDeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ChaloFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authApiService: AuthApiService
    @Inject lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed: $token")
        serviceScope.launch {
            try {
                // Only register if user is logged in
                val userId = userPreferences.userId.first()
                if (userId != null) {
                    authApiService.registerDeviceToken(RegisterDeviceTokenRequest(token))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to register FCM token")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM message received: ${message.data}")

        val type    = message.data["type"] ?: return
        val title   = message.notification?.title ?: message.data["title"] ?: return
        val body    = message.notification?.body  ?: message.data["body"]  ?: return
        val rideId  = message.data["rideId"]

        val channelId = when (type) {
            "DRIVER_ASSIGNED", "RIDE_STARTED", "RIDE_COMPLETED",
            "RIDE_CANCELLED", "DRIVER_ARRIVED" -> CHANNEL_RIDES
            else -> CHANNEL_PROMO
        }

        showNotification(
            title     = title,
            body      = body,
            channelId = channelId,
            rideId    = rideId,
            type      = type,
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        channelId: String,
        rideId: String?,
        type: String,
    ) {
        val notificationId = System.currentTimeMillis().toInt()

        // Deep link intent — MainActivity handles navigation based on extras
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
            if (rideId != null) putExtra(EXTRA_RIDE_ID, rideId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_RIDES = "chalo_rides"
        const val CHANNEL_PROMO = "chalo_promo"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val EXTRA_RIDE_ID = "ride_id"
    }
}
