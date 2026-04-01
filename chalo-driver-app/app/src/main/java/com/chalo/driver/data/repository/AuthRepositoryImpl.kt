package com.chalo.driver.data.repository

import com.chalo.driver.data.local.preferences.DriverPreferences
import com.chalo.driver.data.remote.api.AuthApiService
import com.chalo.driver.data.remote.dto.RegisterDeviceTokenRequest
import com.chalo.driver.data.remote.dto.RegisterDriverRequest
import com.chalo.driver.data.remote.dto.SendOtpRequest
import com.chalo.driver.domain.model.DriverProfile
import com.chalo.driver.domain.model.DriverProfileDetail
import com.chalo.driver.domain.model.User
import com.chalo.driver.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val prefs: DriverPreferences,
) : AuthRepository {

    override suspend fun sendOtp(phone: String): Result<Unit> = runCatching {
        val response = api.sendOtp(SendOtpRequest(phone))
        if (!response.success) error(response.message)
    }

    override suspend fun registerDriver(
        phone: String, otp: String, name: String,
    ): Result<Pair<String, User>> = runCatching {
        val response = api.registerDriver(RegisterDriverRequest(phone, otp, name))
        if (!response.success || response.data == null) error(response.message)
        val data = response.data

        // Sign in to Firebase with the custom token
        FirebaseAuth.getInstance().signInWithCustomToken(data.token).await()

        val user = User(
            id = data.user.id,
            phone = data.user.phone,
            name = data.user.name,
            role = data.user.role,
        )

        // Save to local preferences
        prefs.saveUser(
            userId = user.id,
            name = user.name,
            phone = user.phone,
        )

        Pair(data.token, user)
    }

    override suspend fun getProfile(): Result<DriverProfile> = runCatching {
        val response = api.getProfile()
        if (!response.success || response.data == null) error(response.message)
        val p = response.data
        DriverProfile(
            id = p.id,
            phone = p.phone,
            name = p.name,
            email = p.email,
            role = p.role,
            driverProfile = p.driverProfile?.let { dp ->
                DriverProfileDetail(
                    verificationStatus = dp.verificationStatus,
                    vehicleNumber = dp.vehicleNumber,
                    vehicleModel = dp.vehicleModel,
                    ratingAvg = dp.ratingAvg,
                    ratingCount = dp.ratingCount,
                    totalRides = dp.totalRides,
                    isOnline = dp.isOnline,
                )
            },
        )
    }

    override suspend fun registerDeviceToken(token: String): Result<Unit> = runCatching {
        val response = api.registerDeviceToken(RegisterDeviceTokenRequest(token))
        if (!response.success) error(response.message)
    }

    override suspend fun isLoggedIn(): Boolean {
        return FirebaseAuth.getInstance().currentUser != null &&
                prefs.userId.first() != null
    }

    override suspend fun signOut() {
        FirebaseAuth.getInstance().signOut()
        prefs.clearAll()
    }
}
