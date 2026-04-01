package com.chalo.driver.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegistrationUiState(
    val nameInput: String      = "",
    val isLoading: Boolean     = false,
    val errorMessage: String?  = null,
)

sealed class RegistrationEvent {
    object Success : RegistrationEvent()
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegistrationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(nameInput = name, errorMessage = null) }
    }

    fun onRegister(phone: String, otp: String) {
        val name = _uiState.value.nameInput.trim()
        if (name.length < 2) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            authRepository.registerDriver(phone, otp, name)
                .onSuccess {
                    _events.send(RegistrationEvent.Success)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
