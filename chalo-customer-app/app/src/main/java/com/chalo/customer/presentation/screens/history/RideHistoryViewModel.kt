package com.chalo.customer.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.RideHistoryItem
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RideHistoryUiState {
    object Loading : RideHistoryUiState()
    data class Success(val rides: List<RideHistoryItem>) : RideHistoryUiState()
    data class Error(val message: String) : RideHistoryUiState()
}

@HiltViewModel
class RideHistoryViewModel @Inject constructor(
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RideHistoryUiState>(RideHistoryUiState.Loading)
    val uiState: StateFlow<RideHistoryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RideHistoryUiState.Loading
            rideRepository.getRideHistory(page = 1, status = "ALL")
                .onSuccess { _uiState.value = RideHistoryUiState.Success(it) }
                .onFailure { _uiState.value = RideHistoryUiState.Error(it.message ?: "Failed to load history.") }
        }
    }
}
