package com.chalo.customer.domain.model

data class User(
    val id: String,
    val phone: String,
    val name: String?,
    val role: String,
    val email: String?,
    val languagePref: String?,
    val customerProfile: CustomerProfile?,
)

data class CustomerProfile(
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val savedHomeAddress: String?,
    val savedHomeLat: Double?,
    val savedHomeLng: Double?,
    val savedWorkAddress: String?,
    val savedWorkLat: Double?,
    val savedWorkLng: Double?,
    val totalRides: Int,
    val cancellationCount: Int,
)
