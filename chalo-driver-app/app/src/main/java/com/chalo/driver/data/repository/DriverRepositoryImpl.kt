package com.chalo.driver.data.repository

import com.chalo.driver.data.remote.api.DriverApiService
import com.chalo.driver.data.remote.dto.*
import com.chalo.driver.domain.model.*
import com.chalo.driver.domain.repository.DriverRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverRepositoryImpl @Inject constructor(
    private val api: DriverApiService,
) : DriverRepository {

    // ── Online / Offline ─────────────────────────────────────────

    override suspend fun goOnline(lat: Double, lng: Double): Result<Unit> = runCatching {
        val response = api.goOnline(GoOnlineRequest(lat, lng))
        if (!response.success) error(response.message)
    }

    override suspend fun goOffline(): Result<Unit> = runCatching {
        val response = api.goOffline()
        if (!response.success) error(response.message)
    }

    override suspend fun getStatus(): Result<DriverStatus> = runCatching {
        val response = api.getStatus()
        if (!response.success || response.data == null) error(response.message)
        response.data.toDomain()
    }

    // ── Location ─────────────────────────────────────────────────

    override suspend fun updateLocation(lat: Double, lng: Double): Result<Unit> = runCatching {
        val response = api.updateLocation(UpdateLocationRequest(lat, lng))
        if (!response.success) error(response.message)
    }

    // ── Incoming Ride ────────────────────────────────────────────

    override suspend fun getIncomingRide(): Result<IncomingRide?> = runCatching {
        val response = api.getIncomingRide()
        if (!response.success) error(response.message)
        response.data?.toDomain()
    }

    // ── Ride Lifecycle ───────────────────────────────────────────

    override suspend fun acceptRide(rideId: String): Result<Unit> = runCatching {
        val response = api.acceptRide(rideId)
        if (!response.success) error(response.message)
    }

    override suspend fun declineRide(rideId: String, reason: String?): Result<Unit> = runCatching {
        val response = api.declineRide(rideId, DeclineRideRequest(reason))
        if (!response.success) error(response.message)
    }

    override suspend fun arrivedAtPickup(rideId: String): Result<Unit> = runCatching {
        val response = api.arrivedAtPickup(rideId)
        if (!response.success) error(response.message)
    }

    override suspend fun startRide(rideId: String, otp: String): Result<Unit> = runCatching {
        val response = api.startRide(rideId, StartRideRequest(otp))
        if (!response.success) error(response.message)
    }

    override suspend fun completeRide(rideId: String, note: String?): Result<Unit> = runCatching {
        val response = api.completeRide(rideId, CompleteRideRequest(note))
        if (!response.success) error(response.message)
    }

    override suspend fun cancelRide(rideId: String, reason: String?): Result<Unit> = runCatching {
        val response = api.cancelRide(rideId, DeclineRideRequest(reason))
        if (!response.success) error(response.message)
    }

    override suspend fun rateCustomer(rideId: String, rating: Int, comment: String?): Result<Unit> =
        runCatching {
            val response = api.rateCustomer(rideId, RateCustomerRequest(rating, comment))
            if (!response.success) error(response.message)
        }

    // ── KYC Documents ────────────────────────────────────────────

    override suspend fun submitDocuments(
        licenseNumber: String, licenseUrl: String,
        rcNumber: String, rcUrl: String,
        aadharNumber: String, aadharUrl: String,
        vehicleNumber: String, vehicleModel: String,
        bikePhotoUrl: String?,
    ): Result<DocumentSubmissionResult> = runCatching {
        val response = api.submitDocuments(
            SubmitDocumentsRequest(
                licenseNumber = licenseNumber, licenseUrl = licenseUrl,
                rcNumber = rcNumber, rcUrl = rcUrl,
                aadharNumber = aadharNumber, aadharUrl = aadharUrl,
                vehicleNumber = vehicleNumber, vehicleModel = vehicleModel,
                bikePhotoUrl = bikePhotoUrl,
            )
        )
        if (!response.success || response.data == null) error(response.message)
        val d = response.data
        DocumentSubmissionResult(
            verificationStatus = d.verificationStatus,
            licenseNumber = d.licenseNumber,
            rcNumber = d.rcNumber,
            vehicleNumber = d.vehicleNumber,
            vehicleModel = d.vehicleModel,
        )
    }

    // ── Trip History ─────────────────────────────────────────────

    override suspend fun getTripHistory(page: Int): Result<List<TripHistoryItem>> = runCatching {
        val response = api.getTripHistory(page)
        if (!response.success || response.data == null) error(response.message)
        response.data.map { it.toDomain() }
    }

    // ── Earnings ─────────────────────────────────────────────────

    override suspend fun getEarningsSummary(period: String): Result<EarningsSummary> = runCatching {
        val response = api.getEarningsSummary(period)
        if (!response.success || response.data == null) error(response.message)
        val d = response.data
        EarningsSummary(
            totalEarnings = d.totalEarnings,
            totalRides = d.totalRides,
            totalCommission = d.totalCommission,
            netEarnings = d.netEarnings,
            period = d.period,
        )
    }

    override suspend fun getEarnings(period: String, page: Int): Result<List<EarningsItem>> =
        runCatching {
            val response = api.getEarnings(period, page)
            if (!response.success || response.data == null) error(response.message)
            response.data.map { item ->
                EarningsItem(
                    rideId = item.rideId,
                    fare = item.fare,
                    commission = item.commission,
                    earning = item.earning,
                    completedAt = item.completedAt,
                    pickupAddress = item.pickupAddress,
                    dropAddress = item.dropAddress,
                )
            }
        }

    override suspend fun getSettlementSummary(): Result<SettlementSummary> = runCatching {
        val response = api.getSettlementSummary()
        if (!response.success || response.data == null) error(response.message)
        val d = response.data
        SettlementSummary(
            availableBalance = d.availableBalance,
            pendingSettlement = d.pendingSettlement,
            totalWithdrawn = d.totalWithdrawn,
            nextSettlementDate = d.nextSettlementDate,
        )
    }

    // ── Withdrawals ──────────────────────────────────────────────

    override suspend fun requestWithdrawal(
        amount: Int, method: String,
        upiId: String?, bankAccountNumber: String?, bankIfsc: String?,
    ): Result<WithdrawalResult> = runCatching {
        val response = api.requestWithdrawal(
            WithdrawalRequest(amount, method, upiId, bankAccountNumber, bankIfsc)
        )
        if (!response.success || response.data == null) error(response.message)
        val d = response.data
        WithdrawalResult(d.withdrawalId, d.status, d.amount, d.method)
    }

    override suspend fun getWithdrawalStatus(withdrawalId: String): Result<WithdrawalStatus> =
        runCatching {
            val response = api.getWithdrawalStatus(withdrawalId)
            if (!response.success || response.data == null) error(response.message)
            val d = response.data
            WithdrawalStatus(d.withdrawalId, d.status, d.amount, d.method, d.createdAt, d.processedAt)
        }

    // ── DTO → Domain mappers ─────────────────────────────────────

    private fun DriverStatusDto.toDomain() = DriverStatus(
        isOnline = isOnline,
        verificationStatus = com.chalo.driver.domain.model.VerificationStatus.fromString(verificationStatus),
        currentLocation = currentLocation?.let { DriverLocation(it.lat, it.lng, it.updatedAt) },
        activeRide = activeRide?.toDomain(),
        cancellationStats = cancellationStats?.let {
            CancellationStats(it.total, it.today, it.alertThreshold)
        },
    )

    private fun ActiveRideDto.toDomain() = ActiveRideInfo(
        rideId = rideId,
        status = RideStatus.fromString(status),
        pickupAddress = pickupAddress,
        dropAddress = dropAddress,
        pickupLat = pickupLat ?: 0.0,
        pickupLng = pickupLng ?: 0.0,
        fare = fare,
        paymentMethod = paymentMethod,
        customer = customer?.let { CustomerInfo(it.name, it.phone, it.cancellationCount) },
    )

    private fun IncomingRideDto.toDomain() = IncomingRide(
        rideId = rideId,
        pickup = RideLocation(pickup.lat, pickup.lng, pickup.address),
        drop = RideDropLocation(drop.address),
        distanceKm = distanceKm,
        durationMins = durationMins,
        fare = fare,
        paymentMethod = paymentMethod,
        isScheduled = isScheduled,
        customer = customer?.let { CustomerInfo(it.name, it.phone, it.cancellationCount) },
        offerExpiresInSecs = offerExpiresInSecs,
    )

    private fun TripHistoryItemDto.toDomain() = TripHistoryItem(
        id = id,
        pickupAddress = pickupAddress,
        dropAddress = dropAddress,
        distanceKm = distanceKm,
        finalFare = finalFare,
        driverEarning = driverEarning,
        commissionAmount = commissionAmount,
        status = status,
        paymentMethod = paymentMethod,
        startedAt = startedAt,
        completedAt = completedAt,
        customerName = customer?.name,
    )
}
