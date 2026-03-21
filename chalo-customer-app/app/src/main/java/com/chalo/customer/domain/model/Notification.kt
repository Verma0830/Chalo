package com.chalo.customer.domain.model

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: Long,
)

data class PaymentOrder(
    val razorpayOrderId: String,
    val amount: Int,        // paise
    val currency: String,
    val rideId: String,
)
