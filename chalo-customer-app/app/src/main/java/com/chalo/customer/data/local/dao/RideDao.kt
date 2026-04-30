package com.chalo.customer.data.local.dao

import androidx.room.*
import com.chalo.customer.data.local.entity.RideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {

    @Query("SELECT * FROM rides ORDER BY createdAt DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE id = :rideId LIMIT 1")
    suspend fun getRideById(rideId: String): RideEntity?

    @Query("SELECT * FROM rides WHERE status IN ('REQUESTED','DRIVER_ASSIGNED','DRIVER_ARRIVED','IN_PROGRESS') LIMIT 1")
    fun getActiveRide(): Flow<RideEntity?>

    @Query("SELECT * FROM rides WHERE scheduledAt IS NOT NULL AND status = 'SCHEDULED' ORDER BY scheduledAt ASC")
    fun getScheduledRides(): Flow<List<RideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRide(ride: RideEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRides(rides: List<RideEntity>)

    @Query("UPDATE rides SET status = :status WHERE id = :rideId")
    suspend fun updateStatus(rideId: String, status: String)

    @Query("DELETE FROM rides WHERE id = :rideId")
    suspend fun deleteRide(rideId: String)
}
