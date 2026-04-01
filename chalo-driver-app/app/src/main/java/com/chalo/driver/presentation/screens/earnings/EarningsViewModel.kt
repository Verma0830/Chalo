package com.chalo.driver.presentation.screens.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.model.EarningsItem
import com.chalo.driver.domain.model.EarningsSummary
import com.chalo.driver.domain.model.SettlementSummary
import com.chalo.driver.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EarningsUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: String = "today",
    val summary: EarningsSummary? = null,
    val settlement: SettlementSummary? = null,
    val earningsList: List<EarningsItem> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class EarningsViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EarningsUiState())
    val uiState: StateFlow<EarningsUiState> = _uiState.asStateFlow()

    init {
        loadAll("today")
    }

    fun onPeriodChanged(period: String) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadAll(period)
    }

    private fun loadAll(period: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load summary
            driverRepository.getEarningsSummary(period)
                .onSuccess { summary ->
                    _uiState.update { it.copy(summary = summary) }
                }

            // Load earnings list
            driverRepository.getEarnings(period, 1)
                .onSuccess { list ->
                    _uiState.update { it.copy(earningsList = list) }
                }

            // Load settlement
            driverRepository.getSettlementSummary()
                .onSuccess { settlement ->
                    _uiState.update { it.copy(settlement = settlement) }
                }

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
