package com.chalo.customer.presentation.screens.postride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatingUiState(
    val driverName: String?   = null,
    val rating: Int           = 0,
    val comment: String       = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed class RatingEvent {
    object Done : RatingEvent()
}

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val rideRepository: RideRepository,
    private val userPrefs: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RatingUiState())
    val uiState: StateFlow<RatingUiState> = _uiState.asStateFlow()

    private val _events = Channel<RatingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rideId: String = ""

    fun init(rideId: String) {
        this.rideId = rideId
        viewModelScope.launch {
            rideRepository.getRideDetails(rideId)
                .onSuccess { ride ->
                    _uiState.update { it.copy(driverName = ride.driver?.name) }
                }
        }
        // Check if there's a pending rating from SharedPrefs
        viewModelScope.launch {
            userPrefs.pendingRatingShown.collect { shown ->
                if (shown) userPrefs.markPendingRatingShown()
            }
        }
    }

    fun onRatingSelected(stars: Int) {
        _uiState.update { it.copy(rating = stars) }
    }

    fun onCommentChanged(value: String) {
        _uiState.update { it.copy(comment = value) }
    }

    fun onSubmit() {
        val rating = _uiState.value.rating
        if (rating == 0) return
        val comment = _uiState.value.comment.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            rideRepository.rateRide(rideId, rating, comment)
                .onSuccess {
                    userPrefs.clearPendingRating()
                    _events.send(RatingEvent.Done)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isSubmitting = false) }
                }
        }
    }

    fun onRateLater() {
        viewModelScope.launch {
            // Store pending rating — show once more on next app open
            userPrefs.savePendingRating(rideId)
            _events.send(RatingEvent.Done)
        }
    }
}
