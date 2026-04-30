package com.chalo.customer.data.remote.api

import com.chalo.customer.data.remote.dto.*
import retrofit2.http.*

interface PaymentApiService {

    @POST("payments/order")
    suspend fun createPaymentOrder(@Body body: CreatePaymentOrderRequest): ApiResponse<PaymentOrderDto>

    @POST("payments/verify")
    suspend fun verifyPayment(@Body body: VerifyPaymentRequest): ApiResponse<PaymentVerifyResponseDto>
}
