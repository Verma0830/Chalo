package com.chalo.customer.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Request bodies ───────────────────────────────────────────────

data class SendOtpRequest(
    @SerializedName("phone") val phone: String,
)

data class VerifyOtpRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("otp")   val otp: String,
)

data class CompleteProfileRequest(
    @SerializedName("name")         val name: String,
    @SerializedName("email")        val email: String? = null,
    @SerializedName("languagePref") val languagePref: String = "en",
)

data class UpdateEmergencyContactRequest(
    @SerializedName("emergencyContactName")  val emergencyContactName: String,
    @SerializedName("emergencyContactPhone") val emergencyContactPhone: String,
)

data class RegisterDeviceTokenRequest(
    @SerializedName("fcmToken") val fcmToken: String,
)

data class SavedLocationRequest(
    @SerializedName("type")    val type: String,   // "home" | "work"
    @SerializedName("lat")     val lat: Double,
    @SerializedName("lng")     val lng: Double,
    @SerializedName("address") val address: String,
)

// ── Response DTOs ────────────────────────────────────────────────

data class SendOtpResponseDto(
    @SerializedName("message")   val message: String,
    @SerializedName("expiresIn") val expiresIn: Int,
)

data class VerifyOtpResponseDto(
    @SerializedName("isNewUser") val isNewUser: Boolean,
    @SerializedName("token")     val token: String,      // Firebase custom token
    @SerializedName("user")      val user: UserDto,
)

data class UserDto(
    @SerializedName("id")    val id: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("name")  val name: String?,
    @SerializedName("role")  val role: String,
)

data class ProfileDto(
    @SerializedName("id")                     val id: String,
    @SerializedName("phone")                  val phone: String,
    @SerializedName("name")                   val name: String?,
    @SerializedName("email")                  val email: String?,
    @SerializedName("role")                   val role: String,
    @SerializedName("languagePref")           val languagePref: String?,
    @SerializedName("createdAt")              val createdAt: String,
    @SerializedName("customerProfile")        val customerProfile: CustomerProfileDto?,
)

data class CustomerProfileDto(
    @SerializedName("emergencyContactName")   val emergencyContactName: String?,
    @SerializedName("emergencyContactPhone")  val emergencyContactPhone: String?,
    @SerializedName("savedHomeAddress")       val savedHomeAddress: String?,
    @SerializedName("savedHomeLat")           val savedHomeLat: Double?,
    @SerializedName("savedHomeLng")           val savedHomeLng: Double?,
    @SerializedName("savedWorkAddress")       val savedWorkAddress: String?,
    @SerializedName("savedWorkLat")           val savedWorkLat: Double?,
    @SerializedName("savedWorkLng")           val savedWorkLng: Double?,
    @SerializedName("totalRides")             val totalRides: Int?,
    @SerializedName("cancellationCount")      val cancellationCount: Int?,
)
