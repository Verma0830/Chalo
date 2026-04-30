package com.chalo.driver.util

object Constants {
    // OTP resend cooldown
    const val OTP_RESEND_COOLDOWN_SECS = 60

    // Phone prefix
    const val PHONE_PREFIX = "+91"

    // Ride offer timer
    const val RIDE_OFFER_WINDOW_SECS = 60

    // Location update interval
    const val LOCATION_UPDATE_INTERVAL_MS = 3_000L
    const val LOCATION_FASTEST_INTERVAL_MS = 2_000L

    // KYC polling interval
    const val KYC_POLL_INTERVAL_MS = 30_000L

    // Status polling interval (fallback when RTDB is slow)
    const val STATUS_POLL_INTERVAL_MS = 10_000L
}
