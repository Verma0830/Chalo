package com.chalo.driver.data.remote.api

import com.chalo.driver.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApiService {

    @POST("auth/otp/send")
    suspend fun sendOtp(@Body body: SendOtpRequest): ApiResponse<SendOtpResponseDto>

    @POST("auth/register-driver")
    suspend fun registerDriver(@Body body: RegisterDriverRequest): ApiResponse<RegisterDriverResponseDto>

    @GET("auth/profile")
    suspend fun getProfile(): ApiResponse<ProfileDto>

    @PUT("auth/device-token")
    suspend fun registerDeviceToken(@Body body: RegisterDeviceTokenRequest): ApiResponse<MessageOnlyDto>
}
