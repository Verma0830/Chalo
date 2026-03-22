package com.chalo.customer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreatePaymentOrderRequest(
    @SerialName("rideId") val rideId: String,
)

@Serializable
data class VerifyPaymentRequest(
    @SerialName("razorpayOrderId")   val razorpayOrderId: String,
    @SerialName("razorpayPaymentId") val razorpayPaymentId: String,
    @SerialName("razorpaySignature") val razorpaySignature: String,
)

@Serializable
data class PaymentOrderDto(
    @SerialName("razorpayOrderId") val razorpayOrderId: String,
    @SerialName("amount")          val amount: Int,          // paise
    @SerialName("currency")        val currency: String,
    @SerialName("rideId")          val rideId: String,
)

@Serializable
data class PaymentVerifyResponseDto(
    @SerialName("rideId")        val rideId: String,
    @SerialName("paymentStatus") val paymentStatus: String,
)
