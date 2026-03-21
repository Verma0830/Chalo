package com.chalo.customer.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.AppNotification
import com.chalo.customer.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NotificationsUiState {
    object Loading : NotificationsUiState()
    data class Success(val notifications: List<AppNotification>) : NotificationsUiState()
    data class Error(val message: String) : NotificationsUiState()
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { NotificationsUiState.Loading }
            // Refresh from API, then read from Room
            notificationRepository.refreshNotifications()
            notificationRepository.getNotifications().collect { notifications ->
                _uiState.update { NotificationsUiState.Success(notifications) }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
        }
    }
}
