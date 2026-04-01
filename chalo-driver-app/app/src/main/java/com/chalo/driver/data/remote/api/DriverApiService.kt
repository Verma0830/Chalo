package com.chalo.driver.data.remote.api

import com.chalo.driver.data.remote.dto.*
import retrofit2.http.*

interface DriverApiService {

    // ── Online / Offline ─────────────────────────────────────────

    @POST("driver/go-online")
    suspend fun goOnline(@Body body: GoOnlineRequest): ApiResponse<GoOnlineResponseDto>

    @POST("driver/go-offline")
    suspend fun goOffline(): ApiResponse<GoOfflineResponseDto>

    @GET("driver/status")
    suspend fun getStatus(): ApiResponse<DriverStatusDto>

    // ── Location ─────────────────────────────────────────────────

    @POST("driver/location")
    suspend fun updateLocation(@Body body: UpdateLocationRequest): ApiResponse<UpdateLocationResponseDto>

    // ── Incoming Ride ────────────────────────────────────────────

    @GET("driver/rides/incoming")
    suspend fun getIncomingRide(): ApiResponse<IncomingRideDto?>

    // ── Ride Lifecycle ───────────────────────────────────────────

    @POST("driver/rides/{rideId}/accept")
    suspend fun acceptRide(@Path("rideId") rideId: String): ApiResponse<AcceptRideResponseDto>

    @POST("driver/rides/{rideId}/decline")
    suspend fun declineRide(
        @Path("rideId") rideId: String,
        @Body body: DeclineRideRequest,
    ): ApiResponse<MessageOnlyDto>

    @POST("driver/rides/{rideId}/arrived")
    suspend fun arrivedAtPickup(@Path("rideId") rideId: String): ApiResponse<MessageOnlyDto>

    @POST("driver/rides/{rideId}/start")
    suspend fun startRide(
        @Path("rideId") rideId: String,
        @Body body: StartRideRequest,
    ): ApiResponse<MessageOnlyDto>

    @POST("driver/rides/{rideId}/complete")
    suspend fun completeRide(
        @Path("rideId") rideId: String,
        @Body body: CompleteRideRequest,
    ): ApiResponse<MessageOnlyDto>

    @POST("driver/rides/{rideId}/cancel")
    suspend fun cancelRide(
        @Path("rideId") rideId: String,
        @Body body: DeclineRideRequest,
    ): ApiResponse<MessageOnlyDto>

    @POST("driver/rides/{rideId}/rate-customer")
    suspend fun rateCustomer(
        @Path("rideId") rideId: String,
        @Body body: RateCustomerRequest,
    ): ApiResponse<MessageOnlyDto>

    // ── KYC Documents ────────────────────────────────────────────

    @POST("driver/documents")
    suspend fun submitDocuments(@Body body: SubmitDocumentsRequest): ApiResponse<DocumentSubmissionResponseDto>

    // ── Trip History ─────────────────────────────────────────────

    @GET("driver/trips")
    suspend fun getTripHistory(
        @Query("page")  page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<List<TripHistoryItemDto>>

    // ── Earnings ─────────────────────────────────────────────────

    @GET("driver/earnings/summary")
    suspend fun getEarningsSummary(
        @Query("period") period: String = "week",
    ): ApiResponse<EarningsSummaryDto>

    @GET("driver/earnings")
    suspend fun getEarnings(
        @Query("period") period: String = "today",
        @Query("page")   page: Int = 1,
        @Query("limit")  limit: Int = 20,
    ): ApiResponse<List<EarningsItemDto>>

    @GET("driver/earnings/settlement")
    suspend fun getSettlementSummary(): ApiResponse<SettlementSummaryDto>

    // ── Withdrawals ──────────────────────────────────────────────

    @POST("driver/withdrawals")
    suspend fun requestWithdrawal(@Body body: WithdrawalRequest): ApiResponse<WithdrawalResponseDto>

    @GET("driver/withdrawals/{withdrawalId}")
    suspend fun getWithdrawalStatus(
        @Path("withdrawalId") withdrawalId: String,
    ): ApiResponse<WithdrawalStatusDto>
}
