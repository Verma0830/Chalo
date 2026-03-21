package com.chalo.customer.domain.repository

import com.chalo.customer.domain.model.*
import kotlinx.coroutines.flow.Flow

interface RideRepository {
    suspend fun getFareEstimate(pickup: Location, drop: Location): Result<FareEstimate>
    suspend fun createRide(pickup: Location, drop: Location, paymentMethod: PaymentMethod): Result<Ride>
    suspend fun scheduleRide(pickup: Location, drop: Location, paymentMethod: PaymentMethod, scheduledAt: String): Result<Ride>
    suspend fun getRideDetails(rideId: String): Result<Ride>
    suspend fun getRideHistory(page: Int, status: String): Result<List<RideHistoryItem>>
    suspend fun getScheduledRides(): Result<List<Ride>>
    suspend fun cancelRide(rideId: String, reasonCode: String, note: String?): Result<CancelResult>
    suspend fun rateRide(rideId: String, rating: Int, comment: String?): Result<Unit>
    suspend fun skipRating(rideId: String): Result<Unit>
    suspend fun getRideReceipt(rideId: String): Result<RideReceipt>
    suspend fun shareRide(rideId: String): Result<ShareLink>
    suspend fun triggerSos(rideId: String, lat: Double, lng: Double): Result<Unit>
    fun getActiveRide(): Flow<Ride?>
    fun getCachedRideHistory(): Flow<List<RideHistoryItem>>
}
