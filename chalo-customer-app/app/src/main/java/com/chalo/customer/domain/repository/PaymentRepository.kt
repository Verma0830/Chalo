package com.chalo.customer.domain.repository

import com.chalo.customer.domain.model.PaymentOrder

interface PaymentRepository {
    suspend fun createPaymentOrder(rideId: String): Result<PaymentOrder>
    suspend fun verifyPayment(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
    ): Result<Unit>
}
