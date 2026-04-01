package com.chalo.driver.domain.repository

import com.chalo.driver.domain.model.DriverProfile
import com.chalo.driver.domain.model.User

interface AuthRepository {
    suspend fun sendOtp(phone: String): Result<Unit>
    suspend fun registerDriver(phone: String, otp: String, name: String): Result<Pair<String, User>>
    suspend fun getProfile(): Result<DriverProfile>
    suspend fun registerDeviceToken(token: String): Result<Unit>
    suspend fun isLoggedIn(): Boolean
    suspend fun signOut()
}
