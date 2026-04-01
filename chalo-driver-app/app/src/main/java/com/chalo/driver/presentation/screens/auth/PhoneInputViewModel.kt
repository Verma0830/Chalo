package com.chalo.driver.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.repository.AuthRepository
import com.chalo.driver.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhoneInputUiState(
    val phoneInput: String   = "",
    val phoneError: String?  = null,
    val isLoading: Boolean   = false,
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
        val digits = input.filter { it.isDigit() }.take(10)
        _uiState.update { it.copy(phoneInput = digits, phoneError = null, errorMessage = null) }
    }

    fun onSendOtp() {
        val digits = _uiState.value.phoneInput
        if (digits.length != 10) {
            _uiState.update { it.copy(phoneError = "Enter a valid 10-digit number") }
            return
        }

        val fullPhone = "${Constants.PHONE_PREFIX}$digits"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.sendOtp(fullPhone)
                .onSuccess {
                    _events.send(PhoneInputEvent.OtpSent(fullPhone))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
