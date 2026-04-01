package com.chalo.driver.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.model.VerificationStatus
import com.chalo.driver.domain.repository.AuthRepository
import com.chalo.driver.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class SplashEvent {
    object NavigateToLogin : SplashEvent()
    object NavigateToHome : SplashEvent()
    object NavigateToDocumentSubmit : SplashEvent()
    object NavigateToKycPending : SplashEvent()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _events = Channel<SplashEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            try {
                if (!authRepository.isLoggedIn()) {
                    _events.send(SplashEvent.NavigateToLogin)
                    return@launch
                }

                // Check driver status to determine where to navigate
                driverRepository.getStatus()
                    .onSuccess { status ->
                        when (status.verificationStatus) {
                            VerificationStatus.VERIFIED -> {
                                _events.send(SplashEvent.NavigateToHome)
                            }
                            VerificationStatus.UNDER_REVIEW -> {
                                _events.send(SplashEvent.NavigateToKycPending)
                            }
                            VerificationStatus.REJECTED,
                            VerificationStatus.UNVERIFIED -> {
                                _events.send(SplashEvent.NavigateToDocumentSubmit)
                            }
                        }
                    }
                    .onFailure {
                        Timber.e(it, "Failed to get driver status")
                        // Fallback — if we can't get status but are logged in, try home
                        _events.send(SplashEvent.NavigateToHome)
                    }
            } catch (e: Exception) {
                Timber.e(e, "Splash auth check failed")
                _events.send(SplashEvent.NavigateToLogin)
            }
        }
    }
}
