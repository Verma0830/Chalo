package com.chalo.customer.presentation.navigation

object Routes {
    // Auth
    const val SPLASH          = "splash"
    const val PHONE_INPUT     = "phone_input"
    const val OTP_VERIFY      = "otp_verify/{phone}"
    const val COMPLETE_PROFILE = "complete_profile"

    // Main app
    const val HOME            = "home"
    const val VEHICLE_SELECT  = "vehicle_select/{pickupLat}/{pickupLng}/{pickupAddress}/{dropLat}/{dropLng}/{dropAddress}"
    const val FARE_ESTIMATE   = "fare_estimate/{pickupLat}/{pickupLng}/{pickupAddress}/{dropLat}/{dropLng}/{dropAddress}"
    const val ACTIVE_RIDE     = "active_ride/{rideId}"
    const val PAYMENT         = "payment/{rideId}"
    const val RATING          = "rating/{rideId}"
    const val RECEIPT         = "receipt/{rideId}"

    // History
    const val RIDE_HISTORY    = "ride_history"
    const val RIDE_DETAIL     = "ride_detail/{rideId}"

    // Profile
    const val PROFILE         = "profile"
    const val EMERGENCY_CONTACT = "emergency_contact"
    const val SAVED_LOCATIONS = "saved_locations"

    // Notifications
    const val NOTIFICATIONS   = "notifications"

    // Scheduled
    const val SCHEDULE_RIDE   = "schedule_ride"
    const val SCHEDULED_LIST  = "scheduled_list"

    // ── Builder functions ────────────────────────────────────────

    fun otpVerify(phone: String)   = "otp_verify/${phone.removePrefix("+")}"
    fun activeRide(rideId: String) = "active_ride/$rideId"
    fun payment(rideId: String)    = "payment/$rideId"
    fun rating(rideId: String)     = "rating/$rideId"
    fun receipt(rideId: String)    = "receipt/$rideId"
    fun rideDetail(rideId: String) = "ride_detail/$rideId"

    fun vehicleSelect(
        pickupLat: Double, pickupLng: Double, pickupAddress: String,
        dropLat: Double, dropLng: Double, dropAddress: String,
    ) = "vehicle_select/$pickupLat/$pickupLng/${encode(pickupAddress)}/$dropLat/$dropLng/${encode(dropAddress)}"

    fun fareEstimate(
        pickupLat: Double, pickupLng: Double, pickupAddress: String,
        dropLat: Double,   dropLng: Double,   dropAddress: String,
    ) = "fare_estimate/$pickupLat/$pickupLng/${encode(pickupAddress)}/$dropLat/$dropLng/${encode(dropAddress)}"

    // URL-encode for NavArgs (addresses contain spaces/commas)
    private fun encode(s: String)       = java.net.URLEncoder.encode(s, "UTF-8")
    private fun(s: String)  = java.net.URLEncoder.encode(s, "UTF-8")
}


