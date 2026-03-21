package com.chalo.customer.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RideDetailUiState {
    object Loading : RideDetailUiState()
    data class Success(val ride: Ride) : RideDetailUiState()
    data class Error(val message: String) : RideDetailUiState()
}

@HiltViewModel
class RideDetailViewModel @Inject constructor(
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RideDetailUiState>(RideDetailUiState.Loading)
    val uiState: StateFlow<RideDetailUiState> = _uiState.asStateFlow()

    fun load(rideId: String) {
        viewModelScope.launch {
            _uiState.value = RideDetailUiState.Loading
            rideRepository.getRideDetails(rideId)
                .onSuccess { _uiState.value = RideDetailUiState.Success(it) }
                .onFailure { _uiState.value = RideDetailUiState.Error(it.message ?: "Failed to load ride.") }
        }
    }
}
