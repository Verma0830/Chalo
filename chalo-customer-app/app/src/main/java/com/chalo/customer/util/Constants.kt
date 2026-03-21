package com.chalo.customer.util

object Constants {
    // SOS hold duration before triggering (2 seconds)
    const val SOS_HOLD_DURATION_MS = 2000L

    // Driver arrived within this radius (meters) — matches backend CONSTANTS
    const val DRIVER_ARRIVED_RADIUS_METERS = 200

    // Rating window — after this, pending rating is auto-cleared
    const val RATING_WINDOW_HOURS = 48L

    // OTP resend cooldown
    const val OTP_RESEND_COOLDOWN_SECS = 60

    // Phone prefix
    const val PHONE_PREFIX = "+91"

    // Polling fallback interval (ms) when RTDB is unavailable
    const val LOCATION_POLL_INTERVAL_MS = 5_000L

    // Cancellation reason codes (must match backend enum)
    val CUSTOMER_FAULT_REASON_CODES = setOf(
        "CHANGED_MIND",
        "BOOKED_BY_MISTAKE",
        "OTHER"
    )
    val DRIVER_FAULT_REASON_CODES = setOf(
        "DRIVER_ASKED_TO_CANCEL",
        "DRIVER_NOT_MOVING",
        "DRIVER_WRONG_VEHICLE",
        "DRIVER_BEHAVIOUR"
    )

    // DataStore keys (preference names)
    const val PREF_KEY_USER_ID         = "user_id"
    const val PREF_KEY_USER_NAME       = "user_name"
    const val PREF_KEY_USER_PHONE      = "user_phone"
    const val PREF_KEY_PROFILE_COMPLETE = "profile_complete"
    const val PREF_KEY_PENDING_RATING_RIDE_ID = "pending_rating_ride_id"
    const val PREF_KEY_PENDING_RATING_SHOWN   = "pending_rating_shown_once"
    const val PREF_KEY_PENDING_RATING_TIME    = "pending_rating_timestamp"
}
