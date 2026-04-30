package com.chalo.driver.presentation.screens.earnings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.repository.DriverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WithdrawalUiState(
    val amount: String = "",
    val method: String = "BANK_TRANSFER",
    val upiId: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() {
            val amt = amount.toIntOrNull() ?: return false
            if (amt <= 0 || amt > 50000) return false
            if (method == "BANK_TRANSFER") {
                return upiId.isNotBlank() || (bankAccountNumber.isNotBlank() && bankIfsc.isNotBlank())
            }
            return true
        }
}

sealed class WithdrawalEvent {
    object Success : WithdrawalEvent()
}

@HiltViewModel
class WithdrawalViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WithdrawalUiState())
    val uiState: StateFlow<WithdrawalUiState> = _uiState.asStateFlow()

    private val _events = Channel<WithdrawalEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAmountChanged(v: String) {
        _uiState.update { it.copy(amount = v.filter { c -> c.isDigit() }, errorMessage = null) }
    }
    fun onMethodChanged(method: String) {
        _uiState.update { it.copy(method = method, errorMessage = null) }
    }
    fun onUpiIdChanged(v: String) {
        _uiState.update { it.copy(upiId = v, errorMessage = null) }
    }
    fun onBankAccountChanged(v: String) {
        _uiState.update { it.copy(bankAccountNumber = v, errorMessage = null) }
    }
    fun onBankIfscChanged(v: String) {
        _uiState.update { it.copy(bankIfsc = v.uppercase(), errorMessage = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        val amount = state.amount.toIntOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            driverRepository.requestWithdrawal(
                amount = amount,
                method = state.method,
                upiId = state.upiId.takeIf { it.isNotBlank() },
                bankAccountNumber = state.bankAccountNumber.takeIf { it.isNotBlank() },
                bankIfsc = state.bankIfsc.takeIf { it.isNotBlank() },
            )
                .onSuccess { result ->
                    _uiState.update { it.copy(
                        isSubmitting = false,
                        successMessage = "Withdrawal of \u20B9${result.amount} requested (${result.status})",
                    ) }
                    _events.send(WithdrawalEvent.Success)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = error.message) }
                }
        }
    }
}
