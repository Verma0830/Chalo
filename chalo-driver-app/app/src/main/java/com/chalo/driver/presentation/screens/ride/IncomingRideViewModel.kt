package com.chalo.driver.presentation.screens.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.model.IncomingRide
import com.chalo.driver.domain.repository.DriverRepository
import com.chalo.driver.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class IncomingRideUiState(
    val ride: IncomingRide? = null,
    val isLoading: Boolean = true,
    val timerSecsRemaining: Int = Constants.RIDE_OFFER_WINDOW_SECS,
    val isAccepting: Boolean = false,
    val isDeclining: Boolean = false,
    val errorMessage: String? = null,
)

sealed class IncomingRideEvent {
    data class Accepted(val rideId: String) : IncomingRideEvent()
    object Declined : IncomingRideEvent()
    object Expired : IncomingRideEvent()
}

@HiltViewModel
class IncomingRideViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomingRideUiState())
    val uiState: StateFlow<IncomingRideUiState> = _uiState.asStateFlow()

    private val _events = Channel<IncomingRideEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var timerJob: Job? = null

    init {
        loadIncomingRide()
    }

    private fun loadIncomingRide() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            driverRepository.getIncomingRide()
                .onSuccess { ride ->
                    if (ride != null) {
                        _uiState.update { it.copy(
                            ride = ride,
                            isLoading = false,
                            timerSecsRemaining = ride.offerExpiresInSecs,
                        ) }
                        startCountdown(ride.offerExpiresInSecs)
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                        _events.send(IncomingRideEvent.Expired)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                    _events.send(IncomingRideEvent.Expired)
                }
        }
    }

    private fun startCountdown(totalSecs: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = totalSecs
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.update { it.copy(timerSecsRemaining = remaining) }
            }
            // Timer expired — auto-decline
            _events.send(IncomingRideEvent.Expired)
        }
    }

    fun onAccept() {
        val ride = _uiState.value.ride ?: return
        timerJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isAccepting = true) }
            driverRepository.acceptRide(ride.rideId)
                .onSuccess {
                    _events.send(IncomingRideEvent.Accepted(ride.rideId))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isAccepting = false, errorMessage = error.message) }
                    Timber.e(error, "Failed to accept ride")
                }
        }
    }

    fun onDecline() {
        val ride = _uiState.value.ride ?: return
        timerJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isDeclining = true) }
            driverRepository.declineRide(ride.rideId, null)
                .onSuccess {
                    _events.send(IncomingRideEvent.Declined)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isDeclining = false, errorMessage = error.message) }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
