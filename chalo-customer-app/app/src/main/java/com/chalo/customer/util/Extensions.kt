package com.chalo.customer.util

import kotlin.math.*

/** Format paise (Int) to ₹XX string */
fun Int.toRupees(): String = "₹$this"

/** Format rupees (Double) to ₹XX.XX string */
fun Double.toRupeesFormatted(): String = "₹%.0f".format(this)

/** Haversine distance between two lat/lng points, returns meters */
fun haversineDistanceMeters(
    lat1: Double, lng1: Double,
    lat2: Double, lng2: Double,
): Double {
    val r = 6_371_000.0 // Earth radius in metres
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

/** Validate Indian mobile: +91 followed by 6-9 and 9 more digits */
fun String.isValidIndianPhone(): Boolean =
    matches(Regex("""^\+91[6-9]\d{9}$"""))

/** Mask phone for display: +91 XXXXX 56789 */
fun String.maskPhone(): String {
    val digits = replace("+91", "").trim()
    return if (digits.length == 10) "+91 XXXXX ${digits.takeLast(5)}" else this
}

/** Format epoch millis to readable date string */
fun Long.toReadableDate(): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(this))
}

/** Format duration from minutes */
fun Int.toRideDuration(): String = when {
    this < 60 -> "${this}m"
    else      -> "${this / 60}h ${this % 60}m"
}
