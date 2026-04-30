package com.chalo.driver.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.repository.AuthRepository
import com.chalo.driver.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OtpVerifyUiState(
    val otpInput: String           = "",
    val isLoading: Boolean         = false,
    val errorMessage: String?      = null,
    val resendCooldownSecs: Int    = Constants.OTP_RESEND_COOLDOWN_SECS,
)

sealed class OtpVerifyEvent {
    data class OtpVerified(val phone: String, val otp: String) : OtpVerifyEvent()
}

@HiltViewModel
class OtpVerifyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpVerifyUiState())
    val uiState: StateFlow<OtpVerifyUiState> = _uiState.asStateFlow()

    private val _events = Channel<OtpVerifyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var countdownJob: Job? = null

    init {
        startResendCountdown()
    }

    fun onOtpChanged(input: String, phone: String) {
        val digits = input.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpInput = digits, errorMessage = null) }
        // Auto-submit when 6 digits entered
        if (digits.length == 6) {
            onVerify(phone)
        }
    }

    fun onVerify(phone: String) {
        val otp = _uiState.value.otpInput
        if (otp.length != 6) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            // Just verify OTP is correct format and navigate to registration
            // The actual register-driver call happens in the Registration screen
            _events.send(OtpVerifyEvent.OtpVerified(phone, otp))
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onResendOtp(phone: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            authRepository.sendOtp(phone)
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            _uiState.update { it.copy(resendCooldownSecs = Constants.OTP_RESEND_COOLDOWN_SECS) }
            startResendCountdown()
        }
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_uiState.value.resendCooldownSecs > 0) {
                delay(1000L)
                _uiState.update { it.copy(resendCooldownSecs = it.resendCooldownSecs - 1) }
            }
        }
    }
}
