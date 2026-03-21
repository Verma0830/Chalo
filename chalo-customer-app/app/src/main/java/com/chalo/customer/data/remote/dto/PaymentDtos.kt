package com.chalo.customer.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CreatePaymentOrderRequest(
    @SerializedName("rideId") val rideId: String,
)

data class VerifyPaymentRequest(
    @SerializedName("razorpayOrderId")   val razorpayOrderId: String,
    @SerializedName("razorpayPaymentId") val razorpayPaymentId: String,
    @SerializedName("razorpaySignature") val razorpaySignature: String,
)

data class PaymentOrderDto(
    @SerializedName("razorpayOrderId") val razorpayOrderId: String,
    @SerializedName("amount")          val amount: Int,          // paise
    @SerializedName("currency")        val currency: String,
    @SerializedName("rideId")          val rideId: String,
)

data class PaymentVerifyResponseDto(
    @SerializedName("rideId")        val rideId: String,
    @SerializedName("paymentStatus") val paymentStatus: String,
)
