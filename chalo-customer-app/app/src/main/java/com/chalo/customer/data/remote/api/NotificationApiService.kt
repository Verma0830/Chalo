package com.chalo.customer.data.remote.api

import com.chalo.customer.data.remote.dto.*
import retrofit2.http.*

interface NotificationApiService {

    @GET("notifications")
    suspend fun getNotifications(
        @Query("page")  page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<List<NotificationDto>>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): ApiResponse<UnreadCountDto>

    @PATCH("notifications/{notificationId}/read")
    suspend fun markAsRead(@Path("notificationId") notificationId: String): ApiResponse<Unit>

    @PATCH("notifications/read-all")
    suspend fun markAllAsRead(): ApiResponse<Unit>
}
