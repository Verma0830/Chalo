package com.chalo.driver.presentation.navigation

object Routes {
    // Auth
    const val SPLASH          = "splash"
    const val PHONE_INPUT     = "phone_input"
    const val OTP_VERIFY      = "otp_verify/{phone}"
    const val REGISTRATION    = "registration/{phone}/{otp}"

    // KYC
    const val DOCUMENT_SUBMIT = "document_submit"
    const val KYC_PENDING     = "kyc_pending"

    // Main
    const val HOME            = "home"
    const val INCOMING_RIDE   = "incoming_ride"
    const val ACTIVE_RIDE     = "active_ride/{rideId}"
    const val RATE_CUSTOMER   = "rate_customer/{rideId}"

    // Earnings & History
    const val EARNINGS        = "earnings"
    const val TRIP_HISTORY    = "trip_history"
    const val WITHDRAWAL      = "withdrawal"

    // Profile
    const val PROFILE         = "profile"

    // ── Builder functions ────────────────────────────────────────

    fun otpVerify(phone: String)   = "otp_verify/${phone.removePrefix("+91")}"
    fun registration(phone: String, otp: String) = "registration/${phone.removePrefix("+91")}/$otp"
    fun activeRide(rideId: String) = "active_ride/$rideId"
    fun rateCustomer(rideId: String) = "rate_customer/$rideId"
}
