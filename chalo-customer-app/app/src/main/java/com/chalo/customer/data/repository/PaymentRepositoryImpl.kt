package com.chalo.customer.data.repository

import com.chalo.customer.data.remote.api.PaymentApiService
import com.chalo.customer.data.remote.dto.CreatePaymentOrderRequest
import com.chalo.customer.data.remote.dto.VerifyPaymentRequest
import com.chalo.customer.domain.model.PaymentOrder
import com.chalo.customer.domain.repository.PaymentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepositoryImpl @Inject constructor(
    private val api: PaymentApiService,
) : PaymentRepository {

    override suspend fun createPaymentOrder(rideId: String): Result<PaymentOrder> = runCatching {
        val response = api.createPaymentOrder(CreatePaymentOrderRequest(rideId))
        if (!response.success || response.data == null) error(response.message)
        PaymentOrder(
            razorpayOrderId = response.data.razorpayOrderId,
            amount          = response.data.amount,
            currency        = response.data.currency,
            rideId          = response.data.rideId,
        )
    }

    override suspend fun verifyPayment(
        razorpayOrderId: String,
        razorpayPaymentId: String,
        razorpaySignature: String,
    ): Result<Unit> = runCatching {
        val response = api.verifyPayment(
            VerifyPaymentRequest(razorpayOrderId, razorpayPaymentId, razorpaySignature)
        )
        if (!response.success) error(response.message)
    }
}
