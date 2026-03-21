package com.chalo.customer.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.repository.AuthRepository
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedLocationsUiState(
    val homeAddress: String? = null,
    val workAddress: String? = null,
)

@HiltViewModel
class SavedLocationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedLocationsUiState())
    val uiState: StateFlow<SavedLocationsUiState> = _uiState.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            authRepository.getProfile()
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            homeAddress = user.customerProfile?.savedHomeAddress,
                            workAddress = user.customerProfile?.savedWorkAddress,
                        )
                    }
                }
        }
    }

    fun saveLocation(type: String, lat: Double, lng: Double, address: String) {
        viewModelScope.launch {
            authRepository.updateSavedLocation(type, lat, lng, address)
                .onSuccess {
                    if (type == "home") _uiState.update { it.copy(homeAddress = address) }
                    else                _uiState.update { it.copy(workAddress = address) }
                }
        }
    }
}
