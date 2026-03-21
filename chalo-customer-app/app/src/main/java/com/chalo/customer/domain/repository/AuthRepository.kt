package com.chalo.customer.domain.repository

import com.chalo.customer.domain.model.User

interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun verifyOtp(phone: String, otp: String): Result<VerifyOtpResult>
    suspend fun getProfile(): Result<User>
    suspend fun completeProfile(name: String, email: String?): Result<User>
    suspend fun updateEmergencyContact(name: String, phone: String): Result<Unit>
    suspend fun updateSavedLocation(type: String, lat: Double, lng: Double, address: String): Result<Unit>
    suspend fun registerDeviceToken(fcmToken: String): Result<Unit>
    suspend fun signOut()
}

data class VerifyOtpResult(
    val isNewUser: Boolean,
    val userId: String,
    val userName: String?,
    val userPhone: String,
    val firebaseCustomToken: String,
)
