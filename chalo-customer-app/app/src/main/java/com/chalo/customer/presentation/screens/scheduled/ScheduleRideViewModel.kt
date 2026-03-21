package com.chalo.customer.presentation.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.customer.domain.model.Location
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.domain.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class ScheduleRideUiState(
    val pickupAddress: String  = "",
    val dropAddress: String    = "",
    val scheduledAt: Long?     = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isLoading: Boolean     = false,
    val errorMessage: String?  = null,
    val showDatePicker: Boolean = false,
)

sealed class ScheduleRideEvent {
    data class Scheduled(val rideId: String) : ScheduleRideEvent()
}

@HiltViewModel
class ScheduleRideViewModel @Inject constructor(
    private val rideRepository: RideRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleRideUiState())
    val uiState: StateFlow<ScheduleRideUiState> = _uiState.asStateFlow()

    private val _events = Channel<ScheduleRideEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onPickupChanged(v: String)  = _uiState.update { it.copy(pickupAddress = v, errorMessage = null) }
    fun onDropChanged(v: String)    = _uiState.update { it.copy(dropAddress = v, errorMessage = null) }

    fun onPaymentMethodSelected(method: PaymentMethod) =
        _uiState.update { it.copy(paymentMethod = method) }

    fun showDatePicker() = _uiState.update { it.copy(showDatePicker = true) }
    fun hideDatePicker() = _uiState.update { it.copy(showDatePicker = false) }

    fun onDateTimeSelected(epochMillis: Long) =
        _uiState.update { it.copy(scheduledAt = epochMillis, showDatePicker = false) }

    fun onSchedule() {
        val state = _uiState.value
        if (state.scheduledAt == null) {
            _uiState.update { it.copy(errorMessage = "Please select a date and time") }
            return
        }
        val now = System.currentTimeMillis()
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        if (state.scheduledAt <= now + 5 * 60 * 1000) {
            _uiState.update { it.copy(errorMessage = "Scheduled time must be at least 5 minutes from now") }
            return
        }
        if (state.scheduledAt > now + sevenDays) {
            _uiState.update { it.copy(errorMessage = "Cannot schedule more than 7 days in advance") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Placeholder coords — replace with Google Places geocoding API
            val pickup = Location(lat = 30.7333, lng = 76.7794, address = state.pickupAddress)
            val drop   = Location(lat = 30.7500, lng = 76.7800, address = state.dropAddress)

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val scheduledAtIso = isoFormat.format(Date(state.scheduledAt!!))

            rideRepository.scheduleRide(
                pickup        = pickup,
                drop          = drop,
                paymentMethod = state.paymentMethod,
                scheduledAt   = scheduledAtIso,
            ).onSuccess { ride ->
                _events.send(ScheduleRideEvent.Scheduled(ride.id))
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.message) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
