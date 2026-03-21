package com.chalo.customer.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String?  = null,
    val phone: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userPrefs: UserPreferences,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(userPrefs.userName, userPrefs.userPhone) { name, phone ->
                ProfileUiState(name = name, phone = phone)
            }.collect { state -> _uiState.value = state }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            userPrefs.clearAll()
        }
    }
}
