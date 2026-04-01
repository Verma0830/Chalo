package com.chalo.driver.domain.model

// ── Driver Status ────────────────────────────────────────────────

data class DriverStatus(
    val isOnline: Boolean,
    val verificationStatus: VerificationStatus,
    val currentLocation: DriverLocation?,
    val activeRide: ActiveRideInfo?,
    val cancellationStats: CancellationStats?,
)

data class DriverLocation(
    val lat: Double,
    val lng: Double,
    val updatedAt: String?,
)

data class ActiveRideInfo(
    val rideId: String,
    val status: RideStatus,
    val pickupAddress: String,
    val dropAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val fare: Int?,
    val paymentMethod: String,
    val customer: CustomerInfo?,
)

data class CustomerInfo(
    val name: String?,
    val phone: String?,
    val cancellationCount: Int,
)

data class CancellationStats(
    val total: Int,
    val today: Int,
    val alertThreshold: Int,
)

// ── Incoming Ride Offer ──────────────────────────────────────────

data class IncomingRide(
    val rideId: String,
    val pickup: RideLocation,
    val drop: RideDropLocation,
    val distanceKm: Double?,
    val durationMins: Int?,
    val fare: Int?,
    val paymentMethod: String,
    val isScheduled: Boolean,
    val customer: CustomerInfo?,
    val offerExpiresInSecs: Int,
)

data class RideLocation(
    val lat: Double,
    val lng: Double,
    val address: String,
)

data class RideDropLocation(
    val address: String,
)

// ── Trip History ─────────────────────────────────────────────────

data class TripHistoryItem(
    val id: String,
    val pickupAddress: String,
    val dropAddress: String,
    val distanceKm: Double?,
    val finalFare: Int?,
    val driverEarning: Int?,
    val commissionAmount: Int?,
    val status: String,
    val paymentMethod: String,
    val startedAt: String?,
    val completedAt: String?,
    val customerName: String?,
)

// ── Earnings ─────────────────────────────────────────────────────

data class EarningsSummary(
    val totalEarnings: Int,
    val totalRides: Int,
    val totalCommission: Int,
    val netEarnings: Int,
    val period: String,
)

data class EarningsItem(
    val rideId: String,
    val fare: Int,
    val commission: Int,
    val earning: Int,
    val completedAt: String,
    val pickupAddress: String,
    val dropAddress: String,
)

data class SettlementSummary(
    val availableBalance: Int,
    val pendingSettlement: Int,
    val totalWithdrawn: Int,
    val nextSettlementDate: String?,
)

// ── Withdrawal ───────────────────────────────────────────────────

data class WithdrawalResult(
    val withdrawalId: String,
    val status: String,
    val amount: Int,
    val method: String,
)

data class WithdrawalStatus(
    val withdrawalId: String,
    val status: String,
    val amount: Int,
    val method: String,
    val createdAt: String,
    val processedAt: String?,
)

// ── KYC ──────────────────────────────────────────────────────────

data class DocumentSubmissionResult(
    val verificationStatus: String,
    val licenseNumber: String,
    val rcNumber: String,
    val vehicleNumber: String,
    val vehicleModel: String,
)

// ── Auth ─────────────────────────────────────────────────────────

data class User(
    val id: String,
    val phone: String,
    val name: String?,
    val role: String,
)

data class DriverProfile(
    val id: String,
    val phone: String,
    val name: String?,
    val email: String?,
    val role: String,
    val driverProfile: DriverProfileDetail?,
)

data class DriverProfileDetail(
    val verificationStatus: String?,
    val vehicleNumber: String?,
    val vehicleModel: String?,
    val ratingAvg: Double?,
    val ratingCount: Int?,
    val totalRides: Int?,
    val isOnline: Boolean?,
)

// ── Enums ────────────────────────────────────────────────────────

enum class RideStatus {
    REQUESTED, DRIVER_ASSIGNED, DRIVER_ARRIVED, IN_PROGRESS,
    COMPLETED, CANCELLED, NO_DRIVER, SCHEDULED;

    companion object {
        fun fromString(value: String): RideStatus =
            entries.firstOrNull { it.name == value } ?: REQUESTED
    }
}

enum class VerificationStatus {
    UNVERIFIED, UNDER_REVIEW, VERIFIED, REJECTED;

    companion object {
        fun fromString(value: String): VerificationStatus =
            entries.firstOrNull { it.name == value } ?: UNVERIFIED
    }
}
