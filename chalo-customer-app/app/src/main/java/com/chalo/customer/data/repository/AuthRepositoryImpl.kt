package com.chalo.customer.data.repository

import com.chalo.customer.data.remote.api.AuthApiService
import com.chalo.customer.data.remote.dto.*
import com.chalo.customer.domain.model.*
import com.chalo.customer.domain.repository.AuthRepository
import com.chalo.customer.domain.repository.VerifyOtpResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
) : AuthRepository {

    override suspend fun sendOtp(phone: String): Result<Unit> = runCatching {
        val response = api.sendOtp(SendOtpRequest(phone))
        if (!response.success) error(response.message)
    }

    override suspend fun verifyOtp(phone: String, otp: String): Result<VerifyOtpResult> =
        runCatching {
            val response = api.verifyOtp(VerifyOtpRequest(phone, otp))
            if (!response.success || response.data == null) error(response.message)
            val dto = response.data
            VerifyOtpResult(
                isNewUser            = dto.isNewUser,
                userId               = dto.user.id,
                userName             = dto.user.name,
                userPhone            = dto.user.phone,
                firebaseCustomToken  = dto.token,
            )
        }

    override suspend fun getProfile(): Result<User> = runCatching {
        val response = api.getProfile()
        if (!response.success || response.data == null) error(response.message)
        response.data.toDomain()
    }

    override suspend fun completeProfile(name: String, email: String?): Result<User> =
        runCatching {
            val response = api.completeProfile(CompleteProfileRequest(name = name, email = email))
            if (!response.success || response.data == null) error(response.message)
            response.data.toDomain()
        }

    override suspend fun updateEmergencyContact(name: String, phone: String): Result<Unit> =
        runCatching {
            val response = api.updateEmergencyContact(
                UpdateEmergencyContactRequest(name, phone)
            )
            if (!response.success) error(response.message)
        }

    override suspend fun updateSavedLocation(
        type: String, lat: Double, lng: Double, address: String,
    ): Result<Unit> = runCatching {
        val response = api.updateSavedLocation(SavedLocationRequest(type, lat, lng, address))
        if (!response.success) error(response.message)
    }

    override suspend fun registerDeviceToken(fcmToken: String): Result<Unit> = runCatching {
        val response = api.registerDeviceToken(RegisterDeviceTokenRequest(fcmToken))
        if (!response.success) error(response.message)
    }

    override suspend fun signOut() {
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Timber.e(e, "Firebase sign-out error")
        }
    }

    // ── Mapper: DTO → Domain ─────────────────────────────────────────────────

    private fun ProfileDto.toDomain() = User(
        id           = id,
        phone        = phone,
        name         = name,
        role         = role,
        email        = email,
        languagePref = languagePref,
        customerProfile = customerProfile?.let {
            CustomerProfile(
                emergencyContactName  = it.emergencyContactName,
                emergencyContactPhone = it.emergencyContactPhone,
                savedHomeAddress      = it.savedHomeAddress,
                savedHomeLat          = it.savedHomeLat,
                savedHomeLng          = it.savedHomeLng,
                savedWorkAddress      = it.savedWorkAddress,
                savedWorkLat          = it.savedWorkLat,
                savedWorkLng          = it.savedWorkLng,
                totalRides            = it.totalRides ?: 0,
                cancellationCount     = it.cancellationCount ?: 0,
            )
        },
    )
}
