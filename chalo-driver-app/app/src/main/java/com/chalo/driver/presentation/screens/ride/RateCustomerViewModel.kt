package com.chalo.driver.presentation.screens.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RateCustomerUiState(
    val rating: Int = 0,
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed class RateCustomerEvent {
    object Done : RateCustomerEvent()
}

@HiltViewModel
class RateCustomerViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RateCustomerUiState())
    val uiState: StateFlow<RateCustomerUiState> = _uiState.asStateFlow()

    private val _events = Channel<RateCustomerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rideId: String = ""

    fun init(rideId: String) {
        this.rideId = rideId
    }

    fun onRatingChanged(rating: Int) {
        _uiState.update { it.copy(rating = rating) }
    }

    fun onCommentChanged(comment: String) {
        _uiState.update { it.copy(comment = comment) }
    }

    fun onSubmit() {
        if (_uiState.value.rating == 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            driverRepository.rateCustomer(
                rideId = rideId,
                rating = _uiState.value.rating,
                comment = _uiState.value.comment.takeIf { it.isNotBlank() },
            )
                .onSuccess { _events.send(RateCustomerEvent.Done) }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = error.message) }
                }
        }
    }

    fun onSkip() {
        viewModelScope.launch { _events.send(RateCustomerEvent.Done) }
    }
}
