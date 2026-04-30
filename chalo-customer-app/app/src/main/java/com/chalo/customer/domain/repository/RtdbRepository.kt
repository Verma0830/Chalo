package com.chalo.customer.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface RtdbRepository {
    /** Emits ride status string from RTDB: "DRIVER_ASSIGNED", "IN_PROGRESS", etc. */
    fun observeRideStatus(rideId: String): Flow<String>

    /** Emits driver's live location updates from rides/{rideId}/driverLocation */
    fun observeDriverLocation(rideId: String): Flow<LatLng?>
}
