package com.chalo.customer.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.repository.AuthRepository
import com.chalo.customer.util.Constants
import com.chalo.customer.util.isValidIndianPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneInputUiState(
    val phoneInput: String    = "",
    val phoneError: String?   = null,
    val isLoading: Boolean    = false,
    val errorMessage: String? = null,
)

sealed class PhoneInputEvent {
    data class OtpSent(val phone: String) : PhoneInputEvent()
}

@HiltViewModel
class PhoneInputViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneInputUiState())
    val uiState: StateFlow<PhoneInputUiState> = _uiState.asStateFlow()

    private val _events = Channel<PhoneInputEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onPhoneChanged(input: String) {
        // Accept only digits, max 10
        val digits = input.filter { it.isDigit() }.take(10)
        _uiState.update { it.copy(phoneInput = digits, phoneError = null, errorMessage = null) }
    }

    fun onSendOtp() {
        val digits = _uiState.value.phoneInput
        val fullPhone = "${Constants.PHONE_PREFIX}$digits"

        if (!fullPhone.isValidIndianPhone()) {
            _uiState.update { it.copy(phoneError = "Enter a valid 10-digit mobile number") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.sendOtp(fullPhone)
                .onSuccess {
                    _events.send(PhoneInputEvent.OtpSent(fullPhone))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Failed to send OTP. Try again.")
                    }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
