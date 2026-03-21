package com.chalo.customer.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Request bodies ───────────────────────────────────────────────

data class LocationDto(
    @SerializedName("lat")     val lat: Double,
    @SerializedName("lng")     val lng: Double,
    @SerializedName("address") val address: String,
)

data class FareEstimateRequest(
    @SerializedName("pickup") val pickup: LocationDto,
    @SerializedName("drop")   val drop: LocationDto,
)

data class CreateRideRequest(
    @SerializedName("pickup")        val pickup: LocationDto,
    @SerializedName("drop")          val drop: LocationDto,
    @SerializedName("paymentMethod") val paymentMethod: String,  // "CASH" | "UPI"
)

data class ScheduleRideRequest(
    @SerializedName("pickup")        val pickup: LocationDto,
    @SerializedName("drop")          val drop: LocationDto,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("scheduledAt")   val scheduledAt: String,    // ISO 8601
)

data class CancelRideRequest(
    @SerializedName("reasonCode") val reasonCode: String,
    @SerializedName("note")       val note: String? = null,
)

data class RateRideRequest(
    @SerializedName("rating")  val rating: Int,
    @SerializedName("comment") val comment: String? = null,
)

data class TriggerSosRequest(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double,
)

// ── Response DTOs ────────────────────────────────────────────────

data class FareEstimateDto(
    @SerializedName("estimatedFare")       val estimatedFare: Int,       // rupees
    @SerializedName("distanceKm")          val distanceKm: Double,
    @SerializedName("durationMins")        val durationMins: Int,
    @SerializedName("baseFare")            val baseFare: Int,
    @SerializedName("bookingFee")          val bookingFee: Int,
    @SerializedName("surgeMultiplier")     val surgeMultiplier: Double,
    @SerializedName("minimumFareApplied")  val minimumFareApplied: Boolean,
    @SerializedName("minimumFare")         val minimumFare: Int,
    /** Encoded Google Maps polyline — empty string when Maps API was unavailable */
    @SerializedName("routePolyline")       val routePolyline: String = "",
)

data class RideDto(
    @SerializedName("id")              val id: String,
    @SerializedName("status")          val status: String,
    @SerializedName("paymentMethod")   val paymentMethod: String,
    @SerializedName("paymentStatus")   val paymentStatus: String?,
    @SerializedName("estimatedFare")   val estimatedFare: Int,
    @SerializedName("finalFare")       val finalFare: Int?,
    @SerializedName("distanceKm")      val distanceKm: Double?,
    @SerializedName("durationMins")    val durationMins: Int?,
    @SerializedName("pickupAddress")   val pickupAddress: String,
    @SerializedName("dropAddress")     val dropAddress: String,
    @SerializedName("pickupLat")       val pickupLat: Double,
    @SerializedName("pickupLng")       val pickupLng: Double,
    @SerializedName("dropLat")         val dropLat: Double?,
    @SerializedName("dropLng")         val dropLng: Double?,
    @SerializedName("rideStartOtp")    val rideStartOtp: String?,
    @SerializedName("customerRating")  val customerRating: Int?,
    @SerializedName("driverRating")    val driverRating: Int?,
    @SerializedName("ratingSkippedAt") val ratingSkippedAt: String?,
    @SerializedName("scheduledAt")     val scheduledAt: String?,
    @SerializedName("createdAt")       val createdAt: String,
    @SerializedName("completedAt")     val completedAt: String?,
    @SerializedName("driver")          val driver: DriverSummaryDto?,
    @SerializedName("cancellationFee") val cancellationFee: Int?,
)

data class DriverSummaryDto(
    @SerializedName("id")            val id: String,
    @SerializedName("name")          val name: String?,
    @SerializedName("driverProfile") val driverProfile: DriverProfileSummaryDto?,
)

data class DriverProfileSummaryDto(
    @SerializedName("vehicleNumber") val vehicleNumber: String?,
    @SerializedName("vehicleModel")  val vehicleModel: String?,
    @SerializedName("ratingAvg")     val ratingAvg: Double?,
    @SerializedName("ratingCount")   val ratingCount: Int?,
)

data class RideLocationDto(
    @SerializedName("driverId")  val driverId: String?,
    @SerializedName("lat")       val lat: Double?,
    @SerializedName("lng")       val lng: Double?,
    @SerializedName("updatedAt") val updatedAt: String?,
)

data class ShareRideResponseDto(
    @SerializedName("shareUrl")   val shareUrl: String,
    @SerializedName("expiresAt")  val expiresAt: String,
)

data class CancelRideResponseDto(
    @SerializedName("rideId")              val rideId: String,
    @SerializedName("status")             val status: String,
    @SerializedName("cancellationFee")    val cancellationFee: Int,
    @SerializedName("cancellationWarning") val cancellationWarning: Boolean?,
)

data class RideReceiptDto(
    @SerializedName("rideId")          val rideId: String,
    @SerializedName("pickupAddress")   val pickupAddress: String,
    @SerializedName("dropAddress")     val dropAddress: String,
    @SerializedName("distanceKm")      val distanceKm: Double,
    @SerializedName("durationMins")    val durationMins: Int,
    @SerializedName("baseFare")        val baseFare: Int,
    @SerializedName("bookingFee")      val bookingFee: Int,
    @SerializedName("surgeMultiplier") val surgeMultiplier: Double,
    @SerializedName("finalFare")       val finalFare: Int,
    @SerializedName("paymentMethod")   val paymentMethod: String,
    @SerializedName("paymentStatus")   val paymentStatus: String,
    @SerializedName("completedAt")     val completedAt: String,
    @SerializedName("driver")          val driver: DriverSummaryDto?,
)

data class RideHistoryItemDto(
    @SerializedName("id")            val id: String,
    @SerializedName("pickupAddress") val pickupAddress: String,
    @SerializedName("dropAddress")   val dropAddress: String,
    @SerializedName("finalFare")     val finalFare: Int?,
    @SerializedName("status")        val status: String,
    @SerializedName("paymentMethod") val paymentMethod: String,
    @SerializedName("distanceKm")    val distanceKm: Double?,
    @SerializedName("customerRating") val customerRating: Int?,
    @SerializedName("createdAt")     val createdAt: String,
    @SerializedName("completedAt")   val completedAt: String?,
    @SerializedName("driver")        val driver: DriverSummaryDto?,
)
