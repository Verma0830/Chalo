package com.chalo.customer.presentation.screens.postride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.repository.PaymentRepository
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

data class PaymentUiState(
    val rideId: String        = "",
    val amountPaise: Int      = 0,
    val isLoading: Boolean    = true,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
)

sealed class PaymentEvent {
    data class PaymentComplete(val rideId: String) : PaymentEvent()
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _events = Channel<PaymentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var razorpayOrderId: String? = null

    fun init(rideId: String) {
        _uiState.update { it.copy(rideId = rideId) }
        viewModelScope.launch {
            rideRepository.getRideDetails(rideId)
                .onSuccess { ride ->
                    _uiState.update { it.copy(amountPaise = (ride.finalFare ?: ride.estimatedFare) * 100, isLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun onPayNow() {
        val rideId = _uiState.value.rideId
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
            paymentRepository.createPaymentOrder(rideId)
                .onSuccess { order ->
                    razorpayOrderId = order.razorpayOrderId
                    // In production, open Razorpay checkout here via Activity reference.
                    // For now, simulate success for cash fallback testing:
                    _events.send(PaymentEvent.PaymentComplete(rideId))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isProcessing = false) }
                }
        }
    }

    /** Called by MainActivity after Razorpay callback (onPaymentSuccess) */
    fun onRazorpaySuccess(paymentId: String, signature: String) {
        val orderId = razorpayOrderId ?: return
        val rideId  = _uiState.value.rideId
        viewModelScope.launch {
            paymentRepository.verifyPayment(orderId, paymentId, signature)
                .onSuccess { _events.send(PaymentEvent.PaymentComplete(rideId)) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = "Payment verification failed: ${error.message}", isProcessing = false) }
                }
        }
    }
}
