package com.chalo.customer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chalo.customer.data.local.dao.NotificationDao
import com.chalo.customer.data.local.dao.RideDao
import com.chalo.customer.data.local.entity.NotificationEntity
import com.chalo.customer.data.local.entity.RideEntity

@Database(
    entities = [RideEntity::class, NotificationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun notificationDao(): NotificationDao
}
