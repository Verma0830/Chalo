package com.chalo.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Request bodies ───────────────────────────────────────────────

@Serializable
data class SendOtpRequest(
    @SerialName("phone") val phone: String,
)

@Serializable
data class RegisterDriverRequest(
    @SerialName("phone") val phone: String,
    @SerialName("otp")   val otp: String,
    @SerialName("name")  val name: String,
)

@Serializable
data class RegisterDeviceTokenRequest(
    @SerialName("fcmToken") val fcmToken: String,
)

// ── Response DTOs ────────────────────────────────────────────────

@Serializable
data class SendOtpResponseDto(
    @SerialName("message")   val message: String,
    @SerialName("expiresIn") val expiresIn: Int,
)

@Serializable
data class RegisterDriverResponseDto(
    @SerialName("isNewUser") val isNewUser: Boolean,
    @SerialName("token")     val token: String,
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
    @SerialName("id")            val id: String,
    @SerialName("phone")         val phone: String,
    @SerialName("name")          val name: String?,
    @SerialName("email")         val email: String?,
    @SerialName("role")          val role: String,
    @SerialName("driverProfile") val driverProfile: DriverProfileDto? = null,
)

@Serializable
data class DriverProfileDto(
    @SerialName("verificationStatus") val verificationStatus: String? = null,
    @SerialName("vehicleNumber")      val vehicleNumber: String? = null,
    @SerialName("vehicleModel")       val vehicleModel: String? = null,
    @SerialName("ratingAvg")          val ratingAvg: Double? = null,
    @SerialName("ratingCount")        val ratingCount: Int? = null,
    @SerialName("totalRides")         val totalRides: Int? = null,
    @SerialName("isOnline")           val isOnline: Boolean? = null,
)
