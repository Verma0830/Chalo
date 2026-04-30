package com.chalo.customer.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.data.local.preferences.UserPreferences
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplashDestination { LOADING, PHONE, PROFILE, HOME }

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userPrefs: UserPreferences,
) : ViewModel() {

    private val _destination = MutableStateFlow(SplashDestination.LOADING)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        checkAuthState()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            delay(1_200) // minimum splash display time
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            _destination.value = when {
                firebaseUser == null -> SplashDestination.PHONE
                !userPrefs.isProfileComplete.first() -> SplashDestination.PROFILE
                else -> SplashDestination.HOME
            }
        }
    }
}
