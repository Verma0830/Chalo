package com.chalo.driver.presentation.screens.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.data.local.preferences.DriverPreferences
import com.chalo.driver.domain.model.ActiveRideInfo
import com.chalo.driver.domain.repository.DriverRepository
import com.chalo.driver.service.LocationForegroundService
import com.chalo.driver.util.Constants
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

data class HomeUiState(
    val isOnline: Boolean = false,
    val isLoading: Boolean = true,
    val isTogglingOnline: Boolean = false,
    val activeRide: ActiveRideInfo? = null,
    val todayRides: Int = 0,
    val todayEarnings: Int = 0,
    val errorMessage: String? = null,
)

sealed class HomeEvent {
    object IncomingRide : HomeEvent()
    data class ActiveRide(val rideId: String) : HomeEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    private val prefs: DriverPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var incomingRidePollingJob: Job? = null

    init {
        loadStatus()
        loadTodayEarnings()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            driverRepository.getStatus()
                .onSuccess { status ->
                    _uiState.update { it.copy(
                        isOnline = status.isOnline,
                        activeRide = status.activeRide,
                        isLoading = false,
                        errorMessage = null,
                    ) }
                    prefs.setOnline(status.isOnline)

                    // If online, start polling for incoming rides
                    if (status.isOnline) {
                        startIncomingRidePolling()
                    }

                    // If there's an active ride, navigate to it
                    status.activeRide?.let { ride ->
                        _events.send(HomeEvent.ActiveRide(ride.rideId))
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = error.message,
                    ) }
                }
        }
    }

    private fun loadTodayEarnings() {
        viewModelScope.launch {
            driverRepository.getEarningsSummary("today")
                .onSuccess { summary ->
                    _uiState.update { it.copy(
                        todayRides = summary.totalRides,
                        todayEarnings = summary.netEarnings,
                    ) }
                }
                .onFailure { Timber.w(it, "Failed to load today earnings") }
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleOnline(hasLocationPermission: Boolean) {
        if (!hasLocationPermission) {
            _uiState.update { it.copy(errorMessage = "Location permission is required to go online") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTogglingOnline = true, errorMessage = null) }

            if (_uiState.value.isOnline) {
                // Go offline
                driverRepository.goOffline()
                    .onSuccess {
                        _uiState.update { it.copy(isOnline = false, isTogglingOnline = false) }
                        prefs.setOnline(false)
                        stopIncomingRidePolling()
                        stopLocationService()
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(isTogglingOnline = false, errorMessage = error.message) }
                    }
            } else {
                // Go online — need current location
                try {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    val location = fusedClient.lastLocation.await()
                    val lat = location?.latitude ?: 28.4089    // Faridabad default
                    val lng = location?.longitude ?: 77.3178

                    driverRepository.goOnline(lat, lng)
                        .onSuccess {
                            _uiState.update { it.copy(isOnline = true, isTogglingOnline = false) }
                            prefs.setOnline(true)
                            startIncomingRidePolling()
                            startLocationService()
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(isTogglingOnline = false, errorMessage = error.message) }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get location for go-online")
                    _uiState.update { it.copy(
                        isTogglingOnline = false,
                        errorMessage = "Could not get your location. Please try again.",
                    ) }
                }
            }
        }
    }

    private fun startIncomingRidePolling() {
        incomingRidePollingJob?.cancel()
        incomingRidePollingJob = viewModelScope.launch {
            while (true) {
                delay(3000L) // Check every 3 seconds
                if (!_uiState.value.isOnline) break

                driverRepository.getIncomingRide()
                    .onSuccess { ride ->
                        if (ride != null) {
                            _events.send(HomeEvent.IncomingRide)
                            break
                        }
                    }
                    .onFailure { Timber.w(it, "Incoming ride poll failed") }
            }
        }
    }

    private fun stopIncomingRidePolling() {
        incomingRidePollingJob?.cancel()
        incomingRidePollingJob = null
    }

    private fun startLocationService() {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    private fun stopLocationService() {
        val intent = Intent(context, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        incomingRidePollingJob?.cancel()
    }
}
