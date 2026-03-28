package com.chalo.customer.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

data class OtpVerifyUiState(
    val otp: String              = "",
    val isLoading: Boolean       = false,
    val errorMessage: String?    = null,
    val resendCountdown: Int     = 60,
)

sealed class OtpVerifyEvent {
    data class Verified(val isNewUser: Boolean) : OtpVerifyEvent()
}

@HiltViewModel
class OtpVerifyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OtpVerifyUiState())
    val uiState: StateFlow<OtpVerifyUiState> = _uiState.asStateFlow()

    private val _events = Channel<OtpVerifyEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var phone: String = ""
    private var countdownJob: Job? = null

    fun init(phone: String) {
        this.phone = phone
        startResendCountdown()
    }

    fun onOtpChanged(value: String) {
        _uiState.update { it.copy(otp = value, errorMessage = null) }
        // Auto-verify when 6 digits entered
        if (value.length == 6) onVerify()
    }

    fun onSmsReceived(otp: String) {
        onOtpChanged(otp)
    }

    fun onVerify() {
        val otp = _uiState.value.otp
        if (otp.length != 6) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            authRepository.verifyOtp(phone, otp)
                .onSuccess { result ->
                    // Sign in to Firebase with the custom token from backend
                    try {
                        FirebaseAuth.getInstance()
                            .signInWithCustomToken(result.firebaseCustomToken)
                            .await()

                        // Persist user info in DataStore
                        userPrefs.saveUser(
                            userId          = result.userId,
                            name            = result.userName,
                            phone           = result.userPhone,
                            profileComplete = !result.isNewUser && result.userName != null,
                        )

                        _events.send(OtpVerifyEvent.Verified(result.isNewUser))
                    } catch (e: Exception) {
                        Timber.e(e, "Firebase signInWithCustomToken failed")
                        _uiState.update { it.copy(errorMessage = "Authentication failed. Please try again.") }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Invalid OTP. Please try again.")
                    }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onResendOtp() {
        viewModelScope.launch {
            authRepository.sendOtp(phone)
                .onSuccess { startResendCountdown() }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 60 downTo 0) {
                _uiState.update { it.copy(resendCountdown = i) }
                if (i > 0) delay(1_000)
            }
        }
    }
}
