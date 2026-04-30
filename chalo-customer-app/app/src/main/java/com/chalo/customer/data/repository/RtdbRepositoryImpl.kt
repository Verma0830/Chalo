package com.chalo.customer.data.repository

import com.chalo.customer.domain.repository.RtdbRepository
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtdbRepositoryImpl @Inject constructor() : RtdbRepository {

    /**
     * Listens to rides/{rideId}/status in Firebase RTDB.
     * Emits the status string whenever it changes.
     * awaitClose removes the listener when the collector cancels.
     */
    override fun observeRideStatus(rideId: String): Flow<String> = callbackFlow {
        val ref = FirebaseDatabase.getInstance().getReference("rides/$rideId/status")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: return
                trySend(status)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.e("RTDB status listener cancelled: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Listens to rides/{rideId}/driverLocation in Firebase RTDB.
     * Emits LatLng? whenever the driver's location updates.
     */
    override fun observeDriverLocation(rideId: String): Flow<LatLng?> = callbackFlow {
        val ref = FirebaseDatabase.getInstance().getReference("rides/$rideId/driverLocation")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").getValue(Double::class.java)
                val lng = snapshot.child("lng").getValue(Double::class.java)
                trySend(if (lat != null && lng != null) LatLng(lat, lng) else null)
            }
            override fun onCancelled(error: DatabaseError) {
                Timber.e("RTDB location listener cancelled: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
