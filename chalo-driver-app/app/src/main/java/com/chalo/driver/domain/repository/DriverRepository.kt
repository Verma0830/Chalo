package com.chalo.driver.domain.repository

import com.chalo.driver.domain.model.*

interface DriverRepository {
    // Online/Offline
    suspend fun goOnline(lat: Double, lng: Double): Result<Unit>
    suspend fun goOffline(): Result<Unit>
    suspend fun getStatus(): Result<DriverStatus>

    // Location
    suspend fun updateLocation(lat: Double, lng: Double): Result<Unit>

    // Incoming Ride
    suspend fun getIncomingRide(): Result<IncomingRide?>

    // Ride Lifecycle
    suspend fun acceptRide(rideId: String): Result<Unit>
    suspend fun declineRide(rideId: String, reason: String?): Result<Unit>
    suspend fun arrivedAtPickup(rideId: String): Result<Unit>
    suspend fun startRide(rideId: String, otp: String): Result<Unit>
    suspend fun completeRide(rideId: String, note: String?): Result<Unit>
    suspend fun cancelRide(rideId: String, reason: String?): Result<Unit>
    suspend fun rateCustomer(rideId: String, rating: Int, comment: String?): Result<Unit>

    // KYC Documents
    suspend fun submitDocuments(
        licenseNumber: String, licenseUrl: String,
        rcNumber: String, rcUrl: String,
        aadharNumber: String, aadharUrl: String,
        vehicleNumber: String, vehicleModel: String,
        bikePhotoUrl: String?,
    ): Result<DocumentSubmissionResult>

    // Trip History
    suspend fun getTripHistory(page: Int): Result<List<TripHistoryItem>>

    // Earnings
    suspend fun getEarningsSummary(period: String): Result<EarningsSummary>
    suspend fun getEarnings(period: String, page: Int): Result<List<EarningsItem>>
    suspend fun getSettlementSummary(): Result<SettlementSummary>

    // Withdrawals
    suspend fun requestWithdrawal(
        amount: Int, method: String,
        upiId: String?, bankAccountNumber: String?, bankIfsc: String?,
    ): Result<WithdrawalResult>
    suspend fun getWithdrawalStatus(withdrawalId: String): Result<WithdrawalStatus>
}
