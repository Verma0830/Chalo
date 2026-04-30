package com.chalo.customer.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.repository.AuthRepository
import com.chalo.customer.util.isValidIndianPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmergencyContactUiState(
    val name: String         = "",
    val phone: String        = "",
    val nameError: String?   = null,
    val phoneError: String?  = null,
    val isLoading: Boolean   = false,
    val errorMessage: String? = null,
)

sealed class EmergencyContactEvent {
    object Saved : EmergencyContactEvent()
}

@HiltViewModel
class EmergencyContactViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmergencyContactUiState())
    val uiState: StateFlow<EmergencyContactUiState> = _uiState.asStateFlow()

    private val _events = Channel<EmergencyContactEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onNameChanged(v: String)  = _uiState.update { it.copy(name = v, nameError = null) }
    fun onPhoneChanged(v: String) = _uiState.update { it.copy(phone = v, phoneError = null) }

    fun onSave() {
        val state = _uiState.value
        var hasError = false

        if (state.name.trim().length < 2) {
            _uiState.update { it.copy(nameError = "Enter a valid name") }
            hasError = true
        }
        if (!state.phone.isValidIndianPhone()) {
            _uiState.update { it.copy(phoneError = "Enter a valid Indian phone number") }
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.updateEmergencyContact(state.name.trim(), state.phone.trim())
                .onSuccess { _events.send(EmergencyContactEvent.Saved) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
