package com.chalo.customer.data.remote.api

import com.chalo.customer.data.remote.dto.*
import retrofit2.http.*

interface AuthApiService {

    @POST("auth/otp/send")
    suspend fun sendOtp(@Body body: SendOtpRequest): ApiResponse<SendOtpResponseDto>

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): ApiResponse<VerifyOtpResponseDto>

    @GET("auth/profile")
    suspend fun getProfile(): ApiResponse<ProfileDto>

    @PUT("auth/profile")
    suspend fun completeProfile(@Body body: CompleteProfileRequest): ApiResponse<ProfileDto>

    @PUT("auth/emergency-contact")
    suspend fun updateEmergencyContact(@Body body: UpdateEmergencyContactRequest): ApiResponse<ProfileDto>

    @PUT("auth/saved-location")
    suspend fun updateSavedLocation(@Body body: SavedLocationRequest): ApiResponse<ProfileDto>

    @PUT("auth/device-token")
    suspend fun registerDeviceToken(@Body body: RegisterDeviceTokenRequest): ApiResponse<Unit>
}
