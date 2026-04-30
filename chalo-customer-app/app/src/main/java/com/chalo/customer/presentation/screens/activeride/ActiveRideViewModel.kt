package com.chalo.customer.presentation.screens.activeride

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.repository.RideRepository
import com.chalo.customer.domain.repository.RtdbRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.pow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val noDriverFound: Boolean    = false,
    val showCancelSheet: Boolean  = false,
    val showSosConfirm: Boolean   = false,
    val isCancelling: Boolean     = false,
)

@HiltViewModel
class ActiveRideViewModel @Inject constructor(
    private val rideRepository: RideRepository,
    private val rtdbRepository: RtdbRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActiveRideUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventChannel = Channel<ActiveRideEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    private var rideId: String = ""
    private var pollingJob: Job? = null
    private val fusedLocationClient: FusedLocationProviderClient? = try {
        LocationServices.getFusedLocationProviderClient(context)
    } catch (e: Exception) {
        null
    }

    fun init(rideId: String) {
        if (this.rideId == rideId) return
        this.rideId = rideId
        loadRideOnce()
        startPolling()
        observeRideStatusFromRtdb()
        observeDriverLocation()
    }

    // Load once immediately on screen open
    private fun loadRideOnce() {
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

    // Poll every 8 seconds as fallback (RTDB is primary)
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(8000L)
                // Stop polling if terminal state reached
                val currentStatus = _uiState.value.ride?.status
                if (currentStatus in listOf(
                    RideStatus.COMPLETED,
                    RideStatus.CANCELLED,
                    RideStatus.NO_DRIVER,
                )) break

                rideRepository.getRideDetails(rideId)
                    .onSuccess { ride ->
                        _uiState.update { it.copy(ride = ride) }
                        handleRideStatus(ride.status)
                    }
                    .onFailure { error ->
                        // Don't crash on 429 – just log and wait
                        Timber.w("Polling failed: ${error.message}")
                    }
            }
        }
    }

    private fun observeRideStatusFromRtdb() {
        viewModelScope.launch {
            rtdbRepository.observeRideStatus(rideId)
                .catch { e -> Timber.e(e, "RTDB status error – falling back to polling") }
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
                .catch { e -> Timber.w(e, "Driver location stream failed") }
                .collect { latLng ->
                    _uiState.update { it.copy(driverLocation = latLng) }
                }
        }
    }

    private fun handleRideStatus(status: RideStatus) {
        when (status) {
            RideStatus.NO_DRIVER -> {
                _uiState.update { it.copy(noDriverFound = true) }
            }
            RideStatus.COMPLETED -> {
                pollingJob?.cancel()
            }
            RideStatus.CANCELLED -> {
                pollingJob?.cancel()
            }
            else -> {}
        }
    }

    fun onCancelTap() {
        _uiState.update { it.copy(showCancelSheet = true) }
    }

    fun onCancelSheetDismiss() {
        _uiState.update { it.copy(showCancelSheet = false) }
    }

    fun doCancelRide(reason: String) {
        _uiState.update { it.copy(isCancelling = true) }
        viewModelScope.launch {
            rideRepository.cancelRide(rideId, reason)
                .onSuccess {
                    _uiState.update { it.copy(isCancelling = false, showCancelSheet = false) }
                    _eventChannel.send(ActiveRideEvent.RideCancelled)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    fun onSOSTap() {
        _uiState.update { it.copy(showSosConfirm = true) }
    }

    fun onSOSConfirm() {
        viewModelScope.launch {
            val (lat, lng) = getCurrentLocation()
            rideRepository.sosAlert(rideId, lat, lng)
                .onSuccess {
                    _eventChannel.send(ActiveRideEvent.SOSAlertSent)
                    _uiState.update { it.copy(showSosConfirm = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun onSOSCancel() {
        _uiState.update { it.copy(showSosConfirm = false) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Pair<Double, Double> {
        val client = fusedLocationClient ?: return pickupFallback()
        return try {
            val location = client.lastLocation.await()
            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                pickupFallback()
            }
        } catch (e: Throwable) {
            Timber.w(e, "SOS location fetch failed – using ride pickup as fallback")
            pickupFallback()
        }
    }

    private fun pickupFallback(): Pair<Double, Double> {
        val ride = _uiState.value.ride
        return Pair(ride?.pickupLat ?: 0.0, ride?.pickupLng ?: 0.0)
    }

    fun onRetry() = loadRideOnce()

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

sealed class ActiveRideEvent {
    object RideCancelled : ActiveRideEvent()
    object SOSAlertSent : ActiveRideEvent()
}
