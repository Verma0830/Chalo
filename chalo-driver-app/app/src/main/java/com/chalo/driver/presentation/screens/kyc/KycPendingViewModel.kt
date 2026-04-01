package com.chalo.driver.presentation.screens.kyc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.data.local.preferences.DriverPreferences
import com.chalo.driver.domain.model.VerificationStatus
import com.chalo.driver.domain.repository.DriverRepository
import com.chalo.driver.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class KycPendingUiState(
    val status: VerificationStatus = VerificationStatus.UNDER_REVIEW,
    val errorMessage: String? = null,
)

sealed class KycPendingEvent {
    object Verified : KycPendingEvent()
    object Rejected : KycPendingEvent()
}

@HiltViewModel
class KycPendingViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val prefs: DriverPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(KycPendingUiState())
    val uiState: StateFlow<KycPendingUiState> = _uiState.asStateFlow()

    private val _events = Channel<KycPendingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                driverRepository.getStatus()
                    .onSuccess { status ->
                        val vs = status.verificationStatus
                        _uiState.update { it.copy(status = vs, errorMessage = null) }
                        prefs.setVerificationStatus(vs.name)

                        when (vs) {
                            VerificationStatus.VERIFIED -> {
                                _events.send(KycPendingEvent.Verified)
                                pollingJob?.cancel()
                                return@launch
                            }
                            VerificationStatus.REJECTED -> {
                                _events.send(KycPendingEvent.Rejected)
                                pollingJob?.cancel()
                                return@launch
                            }
                            else -> { /* keep polling */ }
                        }
                    }
                    .onFailure { error ->
                        Timber.w(error, "KYC status poll failed")
                        _uiState.update { it.copy(errorMessage = "Could not check status. Retrying\u2026") }
                    }

                delay(Constants.KYC_POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
