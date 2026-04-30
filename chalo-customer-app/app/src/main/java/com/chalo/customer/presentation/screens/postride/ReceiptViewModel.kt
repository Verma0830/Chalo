package com.chalo.customer.presentation.screens.postride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.RideReceipt
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReceiptUiState {
    object Loading : ReceiptUiState()
    data class Success(val receipt: RideReceipt) : ReceiptUiState()
    data class Error(val message: String) : ReceiptUiState()
}

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReceiptUiState>(ReceiptUiState.Loading)
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    fun load(rideId: String) {
        viewModelScope.launch {
            _uiState.value = ReceiptUiState.Loading
            rideRepository.getRideReceipt(rideId)
                .onSuccess  { _uiState.value = ReceiptUiState.Success(it) }
                .onFailure  { _uiState.value = ReceiptUiState.Error(it.message ?: "Could not load receipt.") }
        }
    }
}
