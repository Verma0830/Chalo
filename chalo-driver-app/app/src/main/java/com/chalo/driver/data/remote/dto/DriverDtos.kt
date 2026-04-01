package com.chalo.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request bodies ───────────────────────────────────────────────

@Serializable
data class GoOnlineRequest(
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
)

@Serializable
data class UpdateLocationRequest(
    @SerialName("lat") val lat: Double,
    @SerialName("lng") val lng: Double,
)

@Serializable
data class DeclineRideRequest(
    @SerialName("reason") val reason: String? = null,
)

@Serializable
data class StartRideRequest(
    @SerialName("otp") val otp: String,
)

@Serializable
data class CompleteRideRequest(
    @SerialName("note") val note: String? = null,
)

@Serializable
data class RateCustomerRequest(
    @SerialName("rating")  val rating: Int,
    @SerialName("comment") val comment: String? = null,
)

@Serializable
data class SubmitDocumentsRequest(
    @SerialName("licenseNumber") val licenseNumber: String,
    @SerialName("licenseUrl")    val licenseUrl: String,
    @SerialName("rcNumber")      val rcNumber: String,
    @SerialName("rcUrl")         val rcUrl: String,
    @SerialName("aadharNumber")  val aadharNumber: String,
    @SerialName("aadharUrl")     val aadharUrl: String,
    @SerialName("vehicleNumber") val vehicleNumber: String,
    @SerialName("vehicleModel")  val vehicleModel: String,
    @SerialName("bikePhotoUrl")  val bikePhotoUrl: String? = null,
)

@Serializable
data class WithdrawalRequest(
    @SerialName("amount")            val amount: Int,
    @SerialName("method")            val method: String,
    @SerialName("upiId")             val upiId: String? = null,
    @SerialName("bankAccountNumber") val bankAccountNumber: String? = null,
    @SerialName("bankIfsc")          val bankIfsc: String? = null,
)

// ── Response DTOs ────────────────────────────────────────────────

@Serializable
data class GoOnlineResponseDto(
    @SerialName("status") val status: String,
    @SerialName("lat")    val lat: Double,
    @SerialName("lng")    val lng: Double,
)

@Serializable
data class GoOfflineResponseDto(
    @SerialName("status") val status: String,
)

@Serializable
data class DriverStatusDto(
    @SerialName("isOnline")              val isOnline: Boolean,
    @SerialName("verificationStatus")    val verificationStatus: String,
    @SerialName("currentLocation")       val currentLocation: LocationPointDto? = null,
    @SerialName("activeRide")            val activeRide: ActiveRideDto? = null,
    @SerialName("cancellationStats")     val cancellationStats: CancellationStatsDto? = null,
)

@Serializable
data class LocationPointDto(
    @SerialName("lat")       val lat: Double,
    @SerialName("lng")       val lng: Double,
    @SerialName("updatedAt") val updatedAt: String? = null,
)

@Serializable
data class ActiveRideDto(
    @SerialName("rideId")         val rideId: String,
    @SerialName("status")         val status: String,
    @SerialName("pickupAddress")  val pickupAddress: String,
    @SerialName("dropAddress")    val dropAddress: String,
    @SerialName("pickupLat")      val pickupLat: Double? = null,
    @SerialName("pickupLng")      val pickupLng: Double? = null,
    @SerialName("fare")           val fare: Int? = null,
    @SerialName("paymentMethod")  val paymentMethod: String,
    @SerialName("customer")       val customer: CustomerDto? = null,
)

@Serializable
data class CustomerDto(
    @SerialName("name")              val name: String? = null,
    @SerialName("phone")             val phone: String? = null,
    @SerialName("cancellationCount") val cancellationCount: Int = 0,
)

@Serializable
data class CancellationStatsDto(
    @SerialName("total")          val total: Int,
    @SerialName("today")          val today: Int,
    @SerialName("alertThreshold") val alertThreshold: Int,
)

@Serializable
data class IncomingRideDto(
    @SerialName("rideId")             val rideId: String,
    @SerialName("status")             val status: String,
    @SerialName("pickup")             val pickup: PickupLocationDto,
    @SerialName("drop")               val drop: DropLocationDto,
    @SerialName("distanceKm")         val distanceKm: Double? = null,
    @SerialName("durationMins")       val durationMins: Int? = null,
    @SerialName("fare")               val fare: Int? = null,
    @SerialName("paymentMethod")      val paymentMethod: String,
    @SerialName("isScheduled")        val isScheduled: Boolean = false,
    @SerialName("scheduledAt")        val scheduledAt: String? = null,
    @SerialName("customer")           val customer: CustomerDto? = null,
    @SerialName("offerExpiresInSecs") val offerExpiresInSecs: Int = 60,
)

@Serializable
data class PickupLocationDto(
    @SerialName("lat")     val lat: Double,
    @SerialName("lng")     val lng: Double,
    @SerialName("address") val address: String,
)

@Serializable
data class DropLocationDto(
    @SerialName("address") val address: String,
)

@Serializable
data class UpdateLocationResponseDto(
    @SerialName("lat")       val lat: Double,
    @SerialName("lng")       val lng: Double,
    @SerialName("updatedAt") val updatedAt: String,
)

@Serializable
data class DocumentSubmissionResponseDto(
    @SerialName("verificationStatus") val verificationStatus: String,
    @SerialName("licenseNumber")      val licenseNumber: String,
    @SerialName("rcNumber")           val rcNumber: String,
    @SerialName("vehicleNumber")      val vehicleNumber: String,
    @SerialName("vehicleModel")       val vehicleModel: String,
)

@Serializable
data class TripHistoryItemDto(
    @SerialName("id")               val id: String,
    @SerialName("pickupAddress")    val pickupAddress: String,
    @SerialName("dropAddress")      val dropAddress: String,
    @SerialName("distanceKm")       val distanceKm: Double? = null,
    @SerialName("finalFare")        val finalFare: Int? = null,
    @SerialName("driverEarning")    val driverEarning: Int? = null,
    @SerialName("commissionAmount") val commissionAmount: Int? = null,
    @SerialName("status")           val status: String,
    @SerialName("paymentMethod")    val paymentMethod: String,
    @SerialName("startedAt")        val startedAt: String? = null,
    @SerialName("completedAt")      val completedAt: String? = null,
    @SerialName("customer")         val customer: TripCustomerDto? = null,
)

@Serializable
data class TripCustomerDto(
    @SerialName("name") val name: String? = null,
)

@Serializable
data class EarningsSummaryDto(
    @SerialName("totalEarnings")    val totalEarnings: Int = 0,
    @SerialName("totalRides")       val totalRides: Int = 0,
    @SerialName("totalCommission")  val totalCommission: Int = 0,
    @SerialName("netEarnings")      val netEarnings: Int = 0,
    @SerialName("period")           val period: String = "",
)

@Serializable
data class EarningsItemDto(
    @SerialName("rideId")         val rideId: String,
    @SerialName("fare")           val fare: Int = 0,
    @SerialName("commission")     val commission: Int = 0,
    @SerialName("earning")        val earning: Int = 0,
    @SerialName("completedAt")    val completedAt: String,
    @SerialName("pickupAddress")  val pickupAddress: String = "",
    @SerialName("dropAddress")    val dropAddress: String = "",
)

@Serializable
data class SettlementSummaryDto(
    @SerialName("availableBalance")    val availableBalance: Int = 0,
    @SerialName("pendingSettlement")   val pendingSettlement: Int = 0,
    @SerialName("totalWithdrawn")      val totalWithdrawn: Int = 0,
    @SerialName("nextSettlementDate")  val nextSettlementDate: String? = null,
)

@Serializable
data class WithdrawalResponseDto(
    @SerialName("withdrawalId") val withdrawalId: String,
    @SerialName("status")       val status: String,
    @SerialName("amount")       val amount: Int,
    @SerialName("method")       val method: String,
)

@Serializable
data class WithdrawalStatusDto(
    @SerialName("withdrawalId") val withdrawalId: String,
    @SerialName("status")       val status: String,
    @SerialName("amount")       val amount: Int,
    @SerialName("method")       val method: String,
    @SerialName("createdAt")    val createdAt: String,
    @SerialName("processedAt")  val processedAt: String? = null,
)

// ── Generic "message-only" wrapper for fire-and-forget endpoints ─
@Serializable
data class MessageOnlyDto(
    @SerialName("message") val message: String = "",
)

// Accept ride returns the ride object — we only need the ID
@Serializable
data class AcceptRideResponseDto(
    @SerialName("id")     val id: String? = null,
    @SerialName("rideId") val rideId: String? = null,
    @SerialName("status") val status: String? = null,
)
