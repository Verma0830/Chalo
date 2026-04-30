package com.chalo.customer.presentation.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScheduledListUiState {
    object Loading : ScheduledListUiState()
    data class Success(val rides: List<Ride>) : ScheduledListUiState()
    data class Error(val message: String) : ScheduledListUiState()
}

@HiltViewModel
class ScheduledListViewModel @Inject constructor(
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduledListUiState>(ScheduledListUiState.Loading)
    val uiState: StateFlow<ScheduledListUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { ScheduledListUiState.Loading }
            rideRepository.getScheduledRides()
                .onSuccess { rides -> _uiState.update { ScheduledListUiState.Success(rides) } }
                .onFailure { error -> _uiState.update { ScheduledListUiState.Error(error.message ?: "Failed to load scheduled rides") } }
        }
    }
}
