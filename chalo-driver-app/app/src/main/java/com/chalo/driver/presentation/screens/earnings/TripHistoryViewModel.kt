package com.chalo.driver.presentation.screens.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.model.TripHistoryItem
import com.chalo.driver.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripHistoryUiState(
    val trips: List<TripHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasMore: Boolean = false,
    val page: Int = 1,
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripHistoryUiState())
    val uiState: StateFlow<TripHistoryUiState> = _uiState.asStateFlow()

    init {
        loadTrips(1)
    }

    private fun loadTrips(page: Int) {
        viewModelScope.launch {
            if (page == 1) _uiState.update { it.copy(isLoading = true) }
            driverRepository.getTripHistory(page)
                .onSuccess { trips ->
                    _uiState.update { state ->
                        state.copy(
                            trips = if (page == 1) trips else state.trips + trips,
                            isLoading = false,
                            hasMore = trips.size >= 20,
                            page = page,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    fun loadMore() {
        loadTrips(_uiState.value.page + 1)
    }
}
