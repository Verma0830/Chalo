package com.chalo.customer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request bodies ───────────────────────────────────────────────

@Serializable
data class LocationDto(
    @SerialName("lat")     val lat: Double,
    @SerialName("lng")     val lng: Double,
    @SerialName("address") val address: String,
)

@Serializable
data class FareEstimateRequest(
    @SerialName("pickup") val pickup: LocationDto,
    @SerialName("drop")   val drop: LocationDto,
)

@Serializable
data class CreateRideRequest(
    @SerialName("pickup")        val pickup: LocationDto,
    @SerialName("drop")          val drop: LocationDto,
    @SerialName("paymentMethod") val paymentMethod: String,  // "CASH" | "UPI"
)

@Serializable
data class ScheduleRideRequest(
    @SerialName("pickup")        val pickup: LocationDto,
    @SerialName("drop")          val drop: LocationDto,
    @SerialName("paymentMethod") val paymentMethod: String,
    @SerialName("scheduledAt")   val scheduledAt: String,    // ISO 8601
)

@Serializable
data class CancelRideRequest(
    @SerialName("reasonCode") val reasonCode: String,
    @SerialName("note")       val note: String? = null,
)

@Serializable
data class RateRideRequest(
    @SerialName("rating")  val rating: Int,
    @SerialName("comment") val comment: String? = null,
)

@Serializable
data class TriggerSosRequest(
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
)

// ── Response DTOs ────────────────────────────────────────────────

@Serializable
data class FareEstimateDto(
    @SerialName("estimatedFare")      val estimatedFare: Int,       // rupees
    @SerialName("distanceKm")         val distanceKm: Double,
    @SerialName("durationMins")       val durationMins: Int,
    @SerialName("baseFare")           val baseFare: Int,
    @SerialName("bookingFee")         val bookingFee: Int,
    @SerialName("surgeMultiplier")    val surgeMultiplier: Double,
    @SerialName("minimumFareApplied") val minimumFareApplied: Boolean,
    @SerialName("minimumFare")        val minimumFare: Int,
    /** Encoded Google Maps polyline — empty string when Maps API was unavailable */
    @SerialName("routePolyline")      val routePolyline: String = "",
)

@Serializable
data class RideDto(
    @SerialName("id")              val id: String,
    @SerialName("status")          val status: String,
    @SerialName("paymentMethod")   val paymentMethod: String,
    @SerialName("paymentStatus")   val paymentStatus: String?,
    @SerialName("estimatedFare")   val estimatedFare: Int,
    @SerialName("finalFare")       val finalFare: Int?,
    @SerialName("distanceKm")      val distanceKm: Double?,
    @SerialName("durationMins")    val durationMins: Int?,
    @SerialName("pickupAddress")   val pickupAddress: String,
    @SerialName("dropAddress")     val dropAddress: String,
    @SerialName("pickupLat")       val pickupLat: Double,
    @SerialName("pickupLng")       val pickupLng: Double,
    @SerialName("dropLat")         val dropLat: Double?,
    @SerialName("dropLng")         val dropLng: Double?,
    @SerialName("rideStartOtp")    val rideStartOtp: String?,
    @SerialName("customerRating")  val customerRating: Int?,
    @SerialName("driverRating")    val driverRating: Int?,
    @SerialName("ratingSkippedAt") val ratingSkippedAt: String?,
    @SerialName("scheduledAt")     val scheduledAt: String?,
    @SerialName("createdAt")       val createdAt: String,
    @SerialName("completedAt")     val completedAt: String?,
    @SerialName("driver")          val driver: DriverSummaryDto?,
    @SerialName("cancellationFee") val cancellationFee: Int?,
)

@Serializable
data class DriverSummaryDto(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String?,
    @SerialName("driverProfile") val driverProfile: DriverProfileSummaryDto?,
)

@Serializable
data class DriverProfileSummaryDto(
    @SerialName("vehicleNumber") val vehicleNumber: String?,
    @SerialName("vehicleModel")  val vehicleModel: String?,
    @SerialName("ratingAvg")     val ratingAvg: Double?,
    @SerialName("ratingCount")   val ratingCount: Int?,
)

@Serializable
data class RideLocationDto(
    @SerialName("driverId")  val driverId: String?,
    @SerialName("lat")       val lat: Double?,
    @SerialName("lng")       val lng: Double?,
    @SerialName("updatedAt") val updatedAt: String?,
)

@Serializable
data class ShareRideResponseDto(
    @SerialName("shareUrl")  val shareUrl: String,
    @SerialName("expiresAt") val expiresAt: String,
)

@Serializable
data class CancelRideResponseDto(
    @SerialName("rideId")               val rideId: String,
    @SerialName("status")               val status: String,
    @SerialName("cancellationFee")      val cancellationFee: Int,
    @SerialName("cancellationWarning")  val cancellationWarning: Boolean?,
)

@Serializable
data class RideReceiptDto(
    @SerialName("rideId")          val rideId: String,
    @SerialName("pickupAddress")   val pickupAddress: String,
    @SerialName("dropAddress")     val dropAddress: String,
    @SerialName("distanceKm")      val distanceKm: Double,
    @SerialName("durationMins")    val durationMins: Int,
    @SerialName("baseFare")        val baseFare: Int,
    @SerialName("bookingFee")      val bookingFee: Int,
    @SerialName("surgeMultiplier") val surgeMultiplier: Double,
    @SerialName("finalFare")       val finalFare: Int,
    @SerialName("paymentMethod")   val paymentMethod: String,
    @SerialName("paymentStatus")   val paymentStatus: String,
    @SerialName("completedAt")     val completedAt: String,
    @SerialName("driver")          val driver: DriverSummaryDto?,
)

@Serializable
data class RideHistoryItemDto(
    @SerialName("id")              val id: String,
    @SerialName("pickupAddress")   val pickupAddress: String,
    @SerialName("dropAddress")     val dropAddress: String,
    @SerialName("finalFare")       val finalFare: Int?,
    @SerialName("status")          val status: String,
    @SerialName("paymentMethod")   val paymentMethod: String,
    @SerialName("distanceKm")      val distanceKm: Double?,
    @SerialName("customerRating")  val customerRating: Int?,
    @SerialName("createdAt")       val createdAt: String,
    @SerialName("completedAt")     val completedAt: String?,
    @SerialName("driver")          val driver: DriverSummaryDto?,
)
