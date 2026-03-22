package com.chalo.customer.presentation.screens.activeride

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.repository.RideRepository
import com.chalo.customer.domain.repository.RtdbRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.pow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

data class ActiveRideUiState(
    val ride: Ride?               = null,
    val driverLocation: LatLng?   = null,
    val routePolyline: String     = "",   // encoded Google Maps polyline
    val etaMins: Int?             = null, // ETA to pickup (null = not yet calculable)
    val isLoading: Boolean        = true,
    val errorMessage: String?     = null,
    val showCancelSheet: Boolean  = false,
    val showSosConfirm: Boolean   = false,
    val isCancelling: Boolean     = false,
)

sealed class ActiveRideEvent {
    data class RideCompleted(val rideId: String) : ActiveRideEvent()
    data class PaymentRequired(val rideId: String) : ActiveRideEvent()
    object RideNavigateHome : ActiveRideEvent()
    data class ShareUrl(val url: String) : ActiveRideEvent()
    object SosTriggered : ActiveRideEvent()
}

@HiltViewModel
class ActiveRideViewModel @Inject constructor(
    private val rideRepository: RideRepository,
    private val rtdbRepository: RtdbRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    // domain model import needed for Location
    private fun com.chalo.customer.domain.model.Ride.toPickupLocation() =
        com.chalo.customer.domain.model.Location(pickupLat, pickupLng, pickupAddress)
    private fun com.chalo.customer.domain.model.Ride.toDropLocation() =
        com.chalo.customer.domain.model.Location(dropLat ?: pickupLat, dropLng ?: pickupLng, dropAddress)

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(ActiveRideUiState())
    val uiState: StateFlow<ActiveRideUiState> = _uiState.asStateFlow()

    private val _events = Channel<ActiveRideEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rideId: String = ""

    fun init(rideId: String) {
        if (this.rideId == rideId) return
        this.rideId = rideId
        loadRide()
        observeRideStatusFromRtdb()
        observeDriverLocation()
    }

    private fun loadRide() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            rideRepository.getRideDetails(rideId)
                .onSuccess { ride ->
                    _uiState.update { it.copy(ride = ride, isLoading = false) }
                    handleRideStatus(ride.status)
                    fetchRoutePolyline(ride)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                }
        }
    }

    private fun fetchRoutePolyline(ride: Ride) {
        if (ride.dropLat == null || ride.dropLng == null) return
        viewModelScope.launch {
            rideRepository.getFareEstimate(ride.toPickupLocation(), ride.toDropLocation())
                .onSuccess { estimate ->
                    if (estimate.routePolyline.isNotEmpty()) {
                        _uiState.update { it.copy(routePolyline = estimate.routePolyline) }
                    }
                }
                .onFailure { e -> Timber.w(e, "Could not fetch route polyline") }
        }
    }

    private fun observeRideStatusFromRtdb() {
        viewModelScope.launch {
            rtdbRepository.observeRideStatus(rideId)
                .catch { e -> Timber.e(e, "RTDB status error — falling back to polling") }
                .collect { statusStr ->
                    val status = RideStatus.fromString(statusStr)
                    _uiState.update { state ->
                        state.copy(ride = state.ride?.copy(status = status))
                    }
                    handleRideStatus(status)
                }
        }
    }

    private fun observeDriverLocation() {
        viewModelScope.launch {
            rtdbRepository.observeDriverLocation(rideId)
                .catch { e -> Timber.e(e, "RTDB location error") }
                .collect { latLng ->
                    _uiState.update { state ->
                        state.copy(
                            driverLocation = latLng,
                            etaMins        = latLng?.let { calculateEtaMins(it, state.ride) },
                        )
                    }
                }
        }
    }

    /**
     * Rough ETA estimate: Haversine distance from driver to pickup / 20 km/h city speed.
     * Only meaningful while driver is en route to pickup (DRIVER_ASSIGNED status).
     */
    private fun calculateEtaMins(driverPos: LatLng, ride: Ride?): Int? {
        ride ?: return null
        if (ride.status !in listOf(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED)) return null
        val dlat = Math.toRadians(ride.pickupLat - driverPos.latitude)
        val dlng = Math.toRadians(ride.pickupLng - driverPos.longitude)
        val a = Math.sin(dlat / 2).pow(2) +
                Math.cos(Math.toRadians(driverPos.latitude)) *
                Math.cos(Math.toRadians(ride.pickupLat)) *
                Math.sin(dlng / 2).pow(2)
        val distanceKm = 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        // Apply 1.3 road factor, assume 20 km/h avg speed in city traffic
        val roadKm     = distanceKm * 1.3
        val mins       = (roadKm / 20.0 * 60).toInt().coerceAtLeast(1)
        return mins
    }

    private suspend fun handleRideStatus(status: RideStatus) {
        when (status) {
            RideStatus.COMPLETED -> {
                val ride = _uiState.value.ride ?: return
                if (ride.paymentMethod.name == "UPI" && ride.paymentStatus == "PENDING") {
                    _events.send(ActiveRideEvent.PaymentRequired(rideId))
                } else {
                    _events.send(ActiveRideEvent.RideCompleted(rideId))
                }
            }
            RideStatus.CANCELLED, RideStatus.NO_DRIVER -> {
                _events.send(ActiveRideEvent.RideNavigateHome)
            }
            else -> Unit
        }
    }

    fun onCancelClick() {
        _uiState.update { it.copy(showCancelSheet = true) }
    }

    fun onCancelDismiss() {
        _uiState.update { it.copy(showCancelSheet = false) }
    }

    fun onConfirmCancel(reasonCode: String, note: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCancelling = true, showCancelSheet = false) }
            rideRepository.cancelRide(rideId, reasonCode, note)
                .onSuccess {
                    _events.send(ActiveRideEvent.RideNavigateHome)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isCancelling = false) }
                }
        }
    }

    fun onShareRide() {
        viewModelScope.launch {
            rideRepository.shareRide(rideId)
                .onSuccess { link -> _events.send(ActiveRideEvent.ShareUrl(link.shareUrl)) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun onSosClick() {
        _uiState.update { it.copy(showSosConfirm = true) }
    }

    fun onSosDismiss() {
        _uiState.update { it.copy(showSosConfirm = false) }
    }

    fun onSosConfirm() {
        viewModelScope.launch {
            _uiState.update { it.copy(showSosConfirm = false) }
            val (lat, lng) = getCurrentLocation()
            rideRepository.triggerSos(rideId, lat, lng)
                .onSuccess { _events.send(ActiveRideEvent.SosTriggered) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    /** Returns the device's last-known GPS coordinates.
     *  Falls back to the ride pickup point if location permission is absent or unavailable. */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double> {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                pickupFallback()
            }
        } catch (e: Exception) {
            Timber.w(e, "SOS location fetch failed — using ride pickup as fallback")
            pickupFallback()
        }
    }

    private fun pickupFallback(): Pair<Double, Double> {
        val ride = _uiState.value.ride
        return Pair(ride?.pickupLat ?: 0.0, ride?.pickupLng ?: 0.0)
    }

    fun onRetry() = loadRide()
}
