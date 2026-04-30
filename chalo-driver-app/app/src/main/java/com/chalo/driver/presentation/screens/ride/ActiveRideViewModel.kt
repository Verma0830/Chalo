package com.chalo.driver.presentation.screens.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.model.ActiveRideInfo
import com.chalo.driver.domain.model.RideStatus
import com.chalo.driver.domain.repository.DriverRepository
import com.chalo.driver.domain.repository.RtdbRepository
import com.chalo.driver.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ActiveRideUiState(
    val ride: ActiveRideInfo? = null,
    val isLoading: Boolean = true,
    val isActionLoading: Boolean = false,
    val otpInput: String = "",
    val errorMessage: String? = null,
)

sealed class ActiveRideEvent {
    data class Completed(val rideId: String) : ActiveRideEvent()
    object Cancelled : ActiveRideEvent()
}

@HiltViewModel
class ActiveRideViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
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
        loadStatus()
        startPolling()
        observeRideStatus()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            driverRepository.getStatus()
                .onSuccess { status ->
                    val activeRide = status.activeRide
                    if (activeRide != null && activeRide.rideId == rideId) {
                        _uiState.update { it.copy(ride = activeRide, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Ride not found") }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(Constants.STATUS_POLL_INTERVAL_MS)
                val currentStatus = _uiState.value.ride?.status
                if (currentStatus == RideStatus.COMPLETED || currentStatus == RideStatus.CANCELLED) break

                driverRepository.getStatus()
                    .onSuccess { status ->
                        status.activeRide?.let { ride ->
                            if (ride.rideId == rideId) {
                                _uiState.update { it.copy(ride = ride, errorMessage = null) }
                                handleStatus(ride.status)
                            }
                        }
                    }
                    .onFailure { Timber.w(it, "Active ride poll failed") }
            }
        }
    }

    private fun observeRideStatus() {
        viewModelScope.launch {
            rtdbRepository.observeRideStatus(rideId)
                .catch { e -> Timber.e(e, "RTDB ride status error") }
                .collect { statusStr ->
                    val status = RideStatus.fromString(statusStr)
                    _uiState.update { state ->
                        state.copy(ride = state.ride?.copy(status = status))
                    }
                    handleStatus(status)
                }
        }
    }

    private suspend fun handleStatus(status: RideStatus) {
        when (status) {
            RideStatus.COMPLETED -> {
                pollingJob?.cancel()
                _events.send(ActiveRideEvent.Completed(rideId))
            }
            RideStatus.CANCELLED -> {
                pollingJob?.cancel()
                _events.send(ActiveRideEvent.Cancelled)
            }
            else -> {}
        }
    }

    fun onOtpChanged(input: String) {
        val digits = input.filter { it.isDigit() }.take(4)
        _uiState.update { it.copy(otpInput = digits, errorMessage = null) }
    }

    fun onArrived() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, errorMessage = null) }
            driverRepository.arrivedAtPickup(rideId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            ride = state.ride?.copy(status = RideStatus.DRIVER_ARRIVED),
                            isActionLoading = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onStartRide() {
        val otp = _uiState.value.otpInput
        if (otp.length != 4) return

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, errorMessage = null) }
            driverRepository.startRide(rideId, otp)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            ride = state.ride?.copy(status = RideStatus.IN_PROGRESS),
                            isActionLoading = false,
                            otpInput = "",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onCompleteRide() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, errorMessage = null) }
            driverRepository.completeRide(rideId, null)
                .onSuccess {
                    pollingJob?.cancel()
                    _events.send(ActiveRideEvent.Completed(rideId))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onCancelRide() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, errorMessage = null) }
            driverRepository.cancelRide(rideId, null)
                .onSuccess {
                    pollingJob?.cancel()
                    _events.send(ActiveRideEvent.Cancelled)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = error.message) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
