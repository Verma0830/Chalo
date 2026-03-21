package com.chalo.customer.data.repository

import com.chalo.customer.data.local.dao.RideDao
import com.chalo.customer.data.local.entity.RideEntity
import com.chalo.customer.data.remote.api.RideApiService
import com.chalo.customer.data.remote.dto.*
import com.chalo.customer.domain.model.*
import com.chalo.customer.domain.repository.RideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepositoryImpl @Inject constructor(
    private val api: RideApiService,
    private val rideDao: RideDao,
) : RideRepository {

    override suspend fun getFareEstimate(pickup: Location, drop: Location): Result<FareEstimate> =
        runCatching {
            val response = api.getFareEstimate(
                FareEstimateRequest(
                    pickup = LocationDto(pickup.lat, pickup.lng, pickup.address),
                    drop   = LocationDto(drop.lat, drop.lng, drop.address),
                )
            )
            if (!response.success || response.data == null) error(response.message)
            response.data.toDomain()
        }

    override suspend fun createRide(
        pickup: Location, drop: Location, paymentMethod: PaymentMethod,
    ): Result<Ride> = runCatching {
        val idempotencyKey = UUID.randomUUID().toString()
        val response = api.createRide(
            idempotencyKey = idempotencyKey,
            body = CreateRideRequest(
                pickup        = LocationDto(pickup.lat, pickup.lng, pickup.address),
                drop          = LocationDto(drop.lat, drop.lng, drop.address),
                paymentMethod = paymentMethod.name,
            )
        )
        if (!response.success || response.data == null) error(response.message)
        val ride = response.data.toDomain()
        rideDao.insertRide(ride.toEntity())
        ride
    }

    override suspend fun scheduleRide(
        pickup: Location, drop: Location, paymentMethod: PaymentMethod, scheduledAt: String,
    ): Result<Ride> = runCatching {
        val idempotencyKey = UUID.randomUUID().toString()
        val response = api.scheduleRide(
            idempotencyKey = idempotencyKey,
            body = ScheduleRideRequest(
                pickup        = LocationDto(pickup.lat, pickup.lng, pickup.address),
                drop          = LocationDto(drop.lat, drop.lng, drop.address),
                paymentMethod = paymentMethod.name,
                scheduledAt   = scheduledAt,
            )
        )
        if (!response.success || response.data == null) error(response.message)
        val ride = response.data.toDomain()
        rideDao.insertRide(ride.toEntity())
        ride
    }

    override suspend fun getRideDetails(rideId: String): Result<Ride> = runCatching {
        val response = api.getRideDetails(rideId)
        if (!response.success || response.data == null) error(response.message)
        val ride = response.data.toDomain()
        rideDao.insertRide(ride.toEntity())
        ride
    }

    override suspend fun getRideHistory(page: Int, status: String): Result<List<RideHistoryItem>> =
        runCatching {
            val response = api.getRideHistory(page = page, status = status)
            if (!response.success || response.data == null) error(response.message)
            val items = response.data.map { it.toHistoryDomain() }
            // Cache to Room (page 1 only — replace cache on first page)
            if (page == 1) rideDao.insertRides(items.map { it.toEntity() })
            items
        }

    override suspend fun getScheduledRides(): Result<List<Ride>> = runCatching {
        val response = api.getScheduledRides()
        if (!response.success || response.data == null) error(response.message)
        response.data.map { it.toDomain() }
    }

    override suspend fun cancelRide(rideId: String, reasonCode: String, note: String?): Result<CancelResult> =
        runCatching {
            val response = api.cancelRide(rideId, CancelRideRequest(reasonCode, note))
            if (!response.success || response.data == null) error(response.message)
            CancelResult(
                cancellationFee     = response.data.cancellationFee,
                cancellationWarning = response.data.cancellationWarning ?: false,
            )
        }

    override suspend fun rateRide(rideId: String, rating: Int, comment: String?): Result<Unit> =
        runCatching {
            val response = api.rateRide(rideId, RateRideRequest(rating, comment))
            if (!response.success) error(response.message)
        }

    override suspend fun skipRating(rideId: String): Result<Unit> = runCatching {
        val response = api.skipRating(rideId)
        if (!response.success) error(response.message)
    }

    override suspend fun getRideReceipt(rideId: String): Result<RideReceipt> = runCatching {
        val response = api.getRideReceipt(rideId)
        if (!response.success || response.data == null) error(response.message)
        response.data.toDomain()
    }

    override suspend fun shareRide(rideId: String): Result<ShareLink> = runCatching {
        val response = api.shareRide(rideId)
        if (!response.success || response.data == null) error(response.message)
        ShareLink(response.data.shareUrl, response.data.expiresAt)
    }

    override suspend fun triggerSos(rideId: String, lat: Double, lng: Double): Result<Unit> =
        runCatching {
            val response = api.triggerSos(rideId, TriggerSosRequest(lat, lng))
            if (!response.success) error(response.message)
        }

    override fun getActiveRide(): Flow<Ride?> =
        rideDao.getActiveRide().map { it?.toDomain() }

    override fun getCachedRideHistory(): Flow<List<RideHistoryItem>> =
        rideDao.getAllRides().map { list -> list.map { it.toHistoryItem() } }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun FareEstimateDto.toDomain() = FareEstimate(
        estimatedFare      = estimatedFare,
        distanceKm         = distanceKm,
        durationMins       = durationMins,
        baseFare           = baseFare,
        bookingFee         = bookingFee,
        surgeMultiplier    = surgeMultiplier,
        minimumFareApplied = minimumFareApplied,
        minimumFare        = minimumFare,
        routePolyline      = routePolyline,
    )

    private fun RideDto.toDomain() = Ride(
        id            = id,
        status        = RideStatus.fromString(status),
        paymentMethod = PaymentMethod.fromString(paymentMethod),
        paymentStatus = paymentStatus,
        estimatedFare = estimatedFare,
        finalFare     = finalFare,
        distanceKm    = distanceKm,
        durationMins  = durationMins,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        pickupLat     = pickupLat,
        pickupLng     = pickupLng,
        dropLat       = dropLat,
        dropLng       = dropLng,
        rideStartOtp  = rideStartOtp,
        customerRating = customerRating,
        scheduledAt   = scheduledAt?.parseIso(),
        createdAt     = createdAt.parseIso() ?: System.currentTimeMillis(),
        completedAt   = completedAt?.parseIso(),
        driver        = driver?.toDomain(),
        cancellationFee = cancellationFee,
    )

    private fun RideHistoryItemDto.toHistoryDomain() = RideHistoryItem(
        id            = id,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        finalFare     = finalFare,
        status        = RideStatus.fromString(status),
        paymentMethod = PaymentMethod.fromString(paymentMethod),
        distanceKm    = distanceKm,
        customerRating = customerRating,
        createdAt     = createdAt.parseIso() ?: System.currentTimeMillis(),
        completedAt   = completedAt?.parseIso(),
        driverName    = driver?.name,
        vehicleNumber = driver?.driverProfile?.vehicleNumber,
    )

    private fun RideReceiptDto.toDomain() = RideReceipt(
        rideId        = rideId,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        distanceKm    = distanceKm,
        durationMins  = durationMins,
        baseFare      = baseFare,
        bookingFee    = bookingFee,
        surgeMultiplier = surgeMultiplier,
        finalFare     = finalFare,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        completedAt   = completedAt.parseIso() ?: System.currentTimeMillis(),
        driver        = driver?.toDomain(),
    )

    private fun DriverSummaryDto.toDomain() = Driver(
        id            = id,
        name          = name,
        vehicleNumber = driverProfile?.vehicleNumber,
        vehicleModel  = driverProfile?.vehicleModel,
        ratingAvg     = driverProfile?.ratingAvg,
        ratingCount   = driverProfile?.ratingCount,
    )

    private fun Ride.toEntity() = RideEntity(
        id            = id,
        status        = status.name,
        paymentMethod = paymentMethod.name,
        paymentStatus = paymentStatus,
        estimatedFare = estimatedFare,
        finalFare     = finalFare,
        distanceKm    = distanceKm,
        durationMins  = durationMins,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        pickupLat     = pickupLat,
        pickupLng     = pickupLng,
        dropLat       = dropLat,
        dropLng       = dropLng,
        rideStartOtp  = rideStartOtp,
        customerRating = customerRating,
        scheduledAt   = scheduledAt,
        createdAt     = createdAt,
        completedAt   = completedAt,
        driverName    = driver?.name,
        vehicleNumber = driver?.vehicleNumber,
        driverRatingAvg = driver?.ratingAvg,
        cancellationFee = cancellationFee,
    )

    private fun RideHistoryItem.toEntity() = RideEntity(
        id            = id,
        status        = status.name,
        paymentMethod = paymentMethod.name,
        paymentStatus = null,
        estimatedFare = finalFare ?: 0,
        finalFare     = finalFare,
        distanceKm    = distanceKm,
        durationMins  = null,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        pickupLat     = 0.0,
        pickupLng     = 0.0,
        dropLat       = null,
        dropLng       = null,
        rideStartOtp  = null,
        customerRating = customerRating,
        scheduledAt   = null,
        createdAt     = createdAt,
        completedAt   = completedAt,
        driverName    = driverName,
        vehicleNumber = vehicleNumber,
        driverRatingAvg = null,
        cancellationFee = null,
    )

    private fun RideEntity.toDomain() = Ride(
        id            = id,
        status        = RideStatus.fromString(status),
        paymentMethod = PaymentMethod.fromString(paymentMethod),
        paymentStatus = paymentStatus,
        estimatedFare = estimatedFare,
        finalFare     = finalFare,
        distanceKm    = distanceKm,
        durationMins  = durationMins,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        pickupLat     = pickupLat,
        pickupLng     = pickupLng,
        dropLat       = dropLat,
        dropLng       = dropLng,
        rideStartOtp  = rideStartOtp,
        customerRating = customerRating,
        scheduledAt   = scheduledAt,
        createdAt     = createdAt,
        completedAt   = completedAt,
        driver        = if (driverName != null) Driver(
            id = "", name = driverName, vehicleNumber = vehicleNumber,
            vehicleModel = null, ratingAvg = driverRatingAvg, ratingCount = null,
        ) else null,
        cancellationFee = cancellationFee,
    )

    private fun RideEntity.toHistoryItem() = RideHistoryItem(
        id            = id,
        pickupAddress = pickupAddress,
        dropAddress   = dropAddress,
        finalFare     = finalFare,
        status        = RideStatus.fromString(status),
        paymentMethod = PaymentMethod.fromString(paymentMethod),
        distanceKm    = distanceKm,
        customerRating = customerRating,
        createdAt     = createdAt,
        completedAt   = completedAt,
        driverName    = driverName,
        vehicleNumber = vehicleNumber,
    )

    private fun String.parseIso(): Long? = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.parse(this)?.time
    } catch (_: Exception) {
        try {
            val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf2.timeZone = TimeZone.getTimeZone("UTC")
            sdf2.parse(this)?.time
        } catch (_: Exception) { null }
    }
}
