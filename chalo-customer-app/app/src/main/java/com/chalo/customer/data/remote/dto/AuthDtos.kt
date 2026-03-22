package com.chalo.customer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request bodies ───────────────────────────────────────────────

@Serializable
data class SendOtpRequest(
    @SerialName("phone") val phone: String,
)

@Serializable
data class VerifyOtpRequest(
    @SerialName("phone") val phone: String,
    @SerialName("otp")   val otp: String,
)

@Serializable
data class CompleteProfileRequest(
    @SerialName("name")         val name: String,
    @SerialName("email")        val email: String? = null,
    @SerialName("languagePref") val languagePref: String = "en",
)

@Serializable
data class UpdateEmergencyContactRequest(
    @SerialName("emergencyContactName")  val emergencyContactName: String,
    @SerialName("emergencyContactPhone") val emergencyContactPhone: String,
)

@Serializable
data class RegisterDeviceTokenRequest(
    @SerialName("fcmToken") val fcmToken: String,
)

@Serializable
data class SavedLocationRequest(
    @SerialName("type")    val type: String,   // "home" | "work"
    @SerialName("lat")     val lat: Double,
    @SerialName("lng")     val lng: Double,
    @SerialName("address") val address: String,
)

// ── Response DTOs ────────────────────────────────────────────────

@Serializable
data class SendOtpResponseDto(
    @SerialName("message")   val message: String,
    @SerialName("expiresIn") val expiresIn: Int,
)

@Serializable
data class VerifyOtpResponseDto(
    @SerialName("isNewUser") val isNewUser: Boolean,
    @SerialName("token")     val token: String,      // Firebase custom token
    @SerialName("user")      val user: UserDto,
)

@Serializable
data class UserDto(
    @SerialName("id")    val id: String,
    @SerialName("phone") val phone: String,
    @SerialName("name")  val name: String?,
    @SerialName("role")  val role: String,
)

@Serializable
data class ProfileDto(
    @SerialName("id")              val id: String,
    @SerialName("phone")           val phone: String,
    @SerialName("name")            val name: String?,
    @SerialName("email")           val email: String?,
    @SerialName("role")            val role: String,
    @SerialName("languagePref")    val languagePref: String?,
    @SerialName("createdAt")       val createdAt: String,
    @SerialName("customerProfile") val customerProfile: CustomerProfileDto?,
)

@Serializable
data class CustomerProfileDto(
    @SerialName("emergencyContactName")   val emergencyContactName: String?,
    @SerialName("emergencyContactPhone")  val emergencyContactPhone: String?,
    @SerialName("savedHomeAddress")       val savedHomeAddress: String?,
    @SerialName("savedHomeLat")           val savedHomeLat: Double?,
    @SerialName("savedHomeLng")           val savedHomeLng: Double?,
    @SerialName("savedWorkAddress")       val savedWorkAddress: String?,
    @SerialName("savedWorkLat")           val savedWorkLat: Double?,
    @SerialName("savedWorkLng")           val savedWorkLng: Double?,
    @SerialName("totalRides")             val totalRides: Int?,
    @SerialName("cancellationCount")      val cancellationCount: Int?,
)
