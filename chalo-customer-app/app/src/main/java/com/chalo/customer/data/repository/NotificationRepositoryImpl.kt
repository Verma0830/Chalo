package com.chalo.customer.data.repository

import com.chalo.customer.data.local.dao.NotificationDao
import com.chalo.customer.data.local.entity.NotificationEntity
import com.chalo.customer.data.remote.api.NotificationApiService
import com.chalo.customer.domain.model.AppNotification
import com.chalo.customer.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val api: NotificationApiService,
    private val dao: NotificationDao,
) : NotificationRepository {

    override fun getNotifications(): Flow<List<AppNotification>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshNotifications(): Result<Unit> = runCatching {
        val response = api.getNotifications(page = 1, limit = 50)
        if (!response.success || response.data == null) error(response.message)
        dao.insertAll(response.data.map {
            NotificationEntity(
                id        = it.id,
                type      = it.type,
                title     = it.title,
                body      = it.body,
                isRead    = it.isRead,
                createdAt = it.createdAt.parseIso() ?: System.currentTimeMillis(),
            )
        })
    }

    override fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    override suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        api.markAsRead(notificationId)
        dao.markAsRead(notificationId)
    }

    override suspend fun markAllAsRead(): Result<Unit> = runCatching {
        api.markAllAsRead()
        dao.markAllAsRead()
    }

    private fun NotificationEntity.toDomain() = AppNotification(
        id        = id,
        type      = type,
        title     = title,
        body      = body,
        isRead    = isRead,
        createdAt = createdAt,
    )

    private fun String.parseIso(): Long? = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(this)?.time
    } catch (_: Exception) { null }
}
