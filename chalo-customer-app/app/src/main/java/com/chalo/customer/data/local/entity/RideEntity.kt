package com.chalo.customer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey val id: String,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String?,
    val estimatedFare: Int,
    val finalFare: Int?,
    val distanceKm: Double?,
    val durationMins: Int?,
    val pickupAddress: String,
    val dropAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropLat: Double?,
    val dropLng: Double?,
    val rideStartOtp: String?,
    val customerRating: Int?,
    val scheduledAt: Long?,        // epoch millis, null = on-demand
    val createdAt: Long,           // epoch millis
    val completedAt: Long?,
    val driverName: String?,
    val vehicleNumber: String?,
    val driverRatingAvg: Double?,
    val cancellationFee: Int?,
)
