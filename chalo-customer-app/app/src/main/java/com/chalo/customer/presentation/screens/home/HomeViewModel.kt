package com.chalo.customer.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String?   = null,
    val activeRide: Ride?   = null,
    val isLoading: Boolean  = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val rideRepository: RideRepository,
    private val userPrefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeActiveRide()
        loadUserName()
    }

    private fun loadUserName() {
        viewModelScope.launch {
            userPrefs.userName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }
    }

    private fun observeActiveRide() {
        viewModelScope.launch {
            rideRepository.getActiveRide().collect { ride ->
                // Extra safety — ignore terminal status rides that slipped through cache
                if (ride?.status in listOf(RideStatus.NO_DRIVER, RideStatus.CANCELLED, RideStatus.COMPLETED)) {
                    _uiState.update { it.copy(activeRide = null) }
                    return@collect
                }
                _uiState.update { it.copy(activeRide = ride) }
            }
        }
    }
}

