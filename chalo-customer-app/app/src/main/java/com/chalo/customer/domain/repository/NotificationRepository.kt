package com.chalo.customer.domain.repository

import com.chalo.customer.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(): Flow<List<AppNotification>>
    suspend fun refreshNotifications(): Result<Unit>
    fun getUnreadCount(): Flow<Int>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun markAllAsRead(): Result<Unit>
}
