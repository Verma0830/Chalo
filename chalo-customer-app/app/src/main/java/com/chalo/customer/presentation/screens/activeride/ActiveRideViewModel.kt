package com.chalo.customer.presentation.screens.activeride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.repository.RideRepository
import com.chalo.customer.domain.repository.RtdbRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ActiveRideUiState(
    val ride: Ride?               = null,
    val driverLocation: LatLng?   = null,
    val isLoading: Boolean        = true,
    val errorMessage: String?     = null,
    val noDriverFound: Boolean    = false,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActiveRideUiState())
    val uiState: StateFlow<ActiveRideUiState> = _uiState.asStateFlow()

    private val _events = Channel<ActiveRideEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rideId: String = ""
    private var pollingJob: Job? = null

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
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                }
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
                        // Don't crash on 429 — just log and wait
                        Timber.w("Polling failed: ${error.message}")
                    }
            }
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
                    _uiState.update { it.copy(driverLocation = latLng) }
                }
        }
    }

    private suspend fun handleRideStatus(status: RideStatus) {
        when (status) {
            RideStatus.COMPLETED -> {
                pollingJob?.cancel()
                val ride = _uiState.value.ride ?: return
                if (ride.paymentMethod.name == "UPI" && ride.paymentStatus == "PENDING") {
                    _events.send(ActiveRideEvent.PaymentRequired(rideId))
                } else {
                    _events.send(ActiveRideEvent.RideCompleted(rideId))
                }
            }
            RideStatus.NO_DRIVER -> {
                pollingJob?.cancel()
                // Update Room so HomeViewModel stops seeing this as active
                rideRepository.updateLocalRideStatus(rideId, "NO_DRIVER")
                _uiState.update { it.copy(noDriverFound = true) }
            }
            RideStatus.CANCELLED -> {
                pollingJob?.cancel()
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
                    pollingJob?.cancel()
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

    fun onSosConfirm(lat: Double, lng: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(showSosConfirm = false) }
            rideRepository.triggerSos(rideId, lat, lng)
                .onSuccess { _events.send(ActiveRideEvent.SosTriggered) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
        }
    }

    fun onRetry() = loadRideOnce()

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

