package com.chalo.customer.data.remote.api

import com.chalo.customer.data.remote.dto.*
import retrofit2.http.*

interface RideApiService {

    @POST("rides/fare-estimate")
    suspend fun getFareEstimate(@Body body: FareEstimateRequest): ApiResponse<FareEstimateDto>

    @POST("rides")
    suspend fun createRide(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateRideRequest,
    ): ApiResponse<RideDto>

    @POST("rides/schedule")
    suspend fun scheduleRide(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ScheduleRideRequest,
    ): ApiResponse<RideDto>

    @GET("rides/history")
    suspend fun getRideHistory(
        @Query("page")   page: Int = 1,
        @Query("limit")  limit: Int = 20,
        @Query("status") status: String = "ALL",
    ): ApiResponse<List<RideHistoryItemDto>>

    @GET("rides/scheduled")
    suspend fun getScheduledRides(): ApiResponse<List<RideDto>>

    @GET("rides/{rideId}")
    suspend fun getRideDetails(@Path("rideId") rideId: String): ApiResponse<RideDto>

    @GET("rides/{rideId}/location")
    suspend fun getRideLocation(@Path("rideId") rideId: String): ApiResponse<RideLocationDto>

    @GET("rides/{rideId}/receipt")
    suspend fun getRideReceipt(@Path("rideId") rideId: String): ApiResponse<RideReceiptDto>

    @POST("rides/{rideId}/cancel")
    suspend fun cancelRide(
        @Path("rideId") rideId: String,
        @Body body: CancelRideRequest,
    ): ApiResponse<CancelRideResponseDto>

    @POST("rides/{rideId}/share")
    suspend fun shareRide(@Path("rideId") rideId: String): ApiResponse<ShareRideResponseDto>

    @POST("rides/{rideId}/rate")
    suspend fun rateRide(
        @Path("rideId") rideId: String,
        @Body body: RateRideRequest,
    ): ApiResponse<Unit>

    @POST("rides/{rideId}/skip-rating")
    suspend fun skipRating(@Path("rideId") rideId: String): ApiResponse<Unit>

    @POST("rides/{rideId}/sos")
    suspend fun triggerSos(
        @Path("rideId") rideId: String,
        @Body body: TriggerSosRequest,
    ): ApiResponse<Unit>
}
