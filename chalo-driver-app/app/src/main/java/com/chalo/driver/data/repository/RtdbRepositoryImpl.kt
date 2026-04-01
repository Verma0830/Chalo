package com.chalo.driver.data.repository

import com.chalo.driver.BuildConfig
import com.chalo.driver.domain.repository.RtdbRepository
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

    private val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
    }

    override fun observeRideStatus(rideId: String): Flow<String> = callbackFlow {
        val ref = db.getReference("rides/$rideId/status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: return
                trySend(status)
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "RTDB ride status listener cancelled")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeDriverLocation(rideId: String): Flow<LatLng?> = callbackFlow {
        val ref = db.getReference("rides/$rideId/driverLocation")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("lat").getValue(Double::class.java)
                val lng = snapshot.child("lng").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    trySend(LatLng(lat, lng))
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Timber.e(error.toException(), "RTDB driver location listener cancelled")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
