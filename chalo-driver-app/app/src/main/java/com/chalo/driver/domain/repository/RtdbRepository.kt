package com.chalo.driver.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface RtdbRepository {
    fun observeRideStatus(rideId: String): Flow<String>
    fun observeDriverLocation(rideId: String): Flow<LatLng?>
}
