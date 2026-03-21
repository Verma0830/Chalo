package com.chalo.customer.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.FareEstimate
import com.chalo.customer.domain.model.Location
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class FareEstimateUiState {
    object Loading : FareEstimateUiState()
    data class Success(
        val estimate: FareEstimate,
        val selectedPayment: PaymentMethod = PaymentMethod.CASH,
        val isBooking: Boolean = false,
    ) : FareEstimateUiState()
    data class Error(val message: String) : FareEstimateUiState()
}

sealed class FareEstimateEvent {
    data class RideCreated(val rideId: String) : FareEstimateEvent()
}

@HiltViewModel
class FareEstimateViewModel @Inject constructor(
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FareEstimateUiState>(FareEstimateUiState.Loading)
    val uiState: StateFlow<FareEstimateUiState> = _uiState.asStateFlow()

    private val _events = Channel<FareEstimateEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private lateinit var pickup: Location
    private lateinit var drop: Location

    fun load(
        pickupLat: Double, pickupLng: Double, pickupAddress: String,
        dropLat: Double,   dropLng: Double,   dropAddress: String,
    ) {
        pickup = Location(pickupLat, pickupLng, pickupAddress)
        drop   = Location(dropLat, dropLng, dropAddress)
        fetchEstimate()
    }

    private fun fetchEstimate() {
        viewModelScope.launch {
            _uiState.value = FareEstimateUiState.Loading
            rideRepository.getFareEstimate(pickup, drop)
                .onSuccess { estimate ->
                    _uiState.value = FareEstimateUiState.Success(estimate)
                }
                .onFailure { error ->
                    _uiState.value = FareEstimateUiState.Error(
                        error.message ?: "Could not get fare estimate."
                    )
                }
        }
    }

    fun onPaymentMethodSelected(method: PaymentMethod) {
        val current = _uiState.value as? FareEstimateUiState.Success ?: return
        _uiState.value = current.copy(selectedPayment = method)
    }

    fun onBookRide() {
        val current = _uiState.value as? FareEstimateUiState.Success ?: return
        viewModelScope.launch {
            _uiState.value = current.copy(isBooking = true)
            rideRepository.createRide(pickup, drop, current.selectedPayment)
                .onSuccess { ride ->
                    _events.send(FareEstimateEvent.RideCreated(ride.id))
                }
                .onFailure { error ->
                    _uiState.value = FareEstimateUiState.Error(
                        error.message ?: "Could not book ride. Try again."
                    )
                }
        }
    }

    fun onRetry() = fetchEstimate()
}
