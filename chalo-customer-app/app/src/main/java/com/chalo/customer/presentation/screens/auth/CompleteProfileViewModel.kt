package com.chalo.customer.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompleteProfileUiState(
    val name: String          = "",
    val email: String         = "",
    val nameError: String?    = null,
    val isLoading: Boolean    = false,
    val errorMessage: String? = null,
)

sealed class CompleteProfileEvent {
    object Saved : CompleteProfileEvent()
}

@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompleteProfileUiState())
    val uiState: StateFlow<CompleteProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<CompleteProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value, nameError = null) }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun onSave() {
        val name = _uiState.value.name.trim()
        if (name.length < 2) {
            _uiState.update { it.copy(nameError = "Name must be at least 2 characters") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val email = _uiState.value.email.trim().ifBlank { null }

            authRepository.completeProfile(name, email)
                .onSuccess { user ->
                    userPrefs.updateName(user.name ?: name)
                    userPrefs.setProfileComplete(true)
                    _events.send(CompleteProfileEvent.Saved)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Failed to save profile.")
                    }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
