package com.chalo.customer.domain.model

data class Ride(
    val id: String,
    val status: RideStatus,
    val paymentMethod: PaymentMethod,
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
    val rideStartOtp: String?,          // show to customer during DRIVER_ASSIGNED/ARRIVED
    val customerRating: Int?,
    val scheduledAt: Long?,             // epoch millis, null = on-demand
    val createdAt: Long,
    val completedAt: Long?,
    val driver: Driver?,
    val cancellationFee: Int?,
)

data class Driver(
    val id: String,
    val name: String?,
    val vehicleNumber: String?,
    val vehicleModel: String?,
    val ratingAvg: Double?,
    val ratingCount: Int?,
)

data class FareEstimate(
    val estimatedFare: Int,
    val distanceKm: Double,
    val durationMins: Int,
    val baseFare: Int,
    val bookingFee: Int,
    val surgeMultiplier: Double,
    val minimumFareApplied: Boolean,
    val minimumFare: Int,
)

data class Location(
    val lat: Double,
    val lng: Double,
    val address: String,
)

data class RideReceipt(
    val rideId: String,
    val pickupAddress: String,
    val dropAddress: String,
    val distanceKm: Double,
    val durationMins: Int,
    val baseFare: Int,
    val bookingFee: Int,
    val surgeMultiplier: Double,
    val finalFare: Int,
    val paymentMethod: String,
    val paymentStatus: String,
    val completedAt: Long,
    val driver: Driver?,
)

data class RideHistoryItem(
    val id: String,
    val pickupAddress: String,
    val dropAddress: String,
    val finalFare: Int?,
    val status: RideStatus,
    val paymentMethod: PaymentMethod,
    val distanceKm: Double?,
    val customerRating: Int?,
    val createdAt: Long,
    val completedAt: Long?,
    val driverName: String?,
    val vehicleNumber: String?,
)

data class ShareLink(
    val shareUrl: String,
    val expiresAt: String,
)

data class CancelResult(
    val cancellationFee: Int,
    val cancellationWarning: Boolean,
)

enum class RideStatus {
    REQUESTED, DRIVER_ASSIGNED, DRIVER_ARRIVED, IN_PROGRESS,
    COMPLETED, CANCELLED, NO_DRIVER, SCHEDULED;

    companion object {
        fun fromString(value: String): RideStatus =
            entries.firstOrNull { it.name == value } ?: REQUESTED
    }
}

enum class PaymentMethod {
    CASH, UPI;

    companion object {
        fun fromString(value: String): PaymentMethod =
            entries.firstOrNull { it.name == value } ?: CASH
    }
}

enum class VehicleType {
    BIKE, AUTO, CAR, E_RICKSHAW;
    companion object {
        fun fromString(value: String): VehicleType =
            entries.firstOrNull { it.name == value } ?: BIKE
    }
}
