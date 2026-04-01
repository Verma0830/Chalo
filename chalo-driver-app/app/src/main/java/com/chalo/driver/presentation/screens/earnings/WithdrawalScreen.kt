package com.chalo.driver.presentation.screens.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloPrimaryButton
import com.chalo.driver.presentation.theme.ChaloSpacing
import com.chalo.driver.presentation.theme.ChaloSuccess
import kotlinx.coroutines.flow.collectLatest

@Composable
fun WithdrawalScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: WithdrawalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                WithdrawalEvent.Success -> onDone()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Withdraw") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ChaloSpacing.lg),
        ) {
            Spacer(Modifier.height(ChaloSpacing.lg))

            // Amount
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::onAmountChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount (\u20B9)") },
                prefix = { Text("\u20B9 ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(ChaloSpacing.md))

            // Method selector
            Text("Withdrawal Method", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(ChaloSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm)) {
                FilterChip(
                    selected = uiState.method == "BANK_TRANSFER",
                    onClick = { viewModel.onMethodChanged("BANK_TRANSFER") },
                    label = { Text("Bank/UPI") },
                )
                FilterChip(
                    selected = uiState.method == "CASH_AGENT",
                    onClick = { viewModel.onMethodChanged("CASH_AGENT") },
                    label = { Text("Cash Agent") },
                )
            }

            if (uiState.method == "BANK_TRANSFER") {
                Spacer(Modifier.height(ChaloSpacing.md))
                OutlinedTextField(
                    value = uiState.upiId,
                    onValueChange = viewModel::onUpiIdChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("UPI ID (e.g., name@paytm)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(ChaloSpacing.sm))
                Text("Or enter bank details:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(ChaloSpacing.sm))
                OutlinedTextField(
                    value = uiState.bankAccountNumber,
                    onValueChange = viewModel::onBankAccountChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Bank Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(ChaloSpacing.sm))
                OutlinedTextField(
                    value = uiState.bankIfsc,
                    onValueChange = viewModel::onBankIfscChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IFSC Code") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(Modifier.height(ChaloSpacing.xl))

            ChaloPrimaryButton(
                text = "Request Withdrawal",
                onClick = viewModel::onSubmit,
                isLoading = uiState.isSubmitting,
                enabled = uiState.canSubmit,
            )

            uiState.errorMessage?.let { msg ->
                Spacer(Modifier.height(ChaloSpacing.md))
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            uiState.successMessage?.let { msg ->
                Spacer(Modifier.height(ChaloSpacing.md))
                Text(msg, color = ChaloSuccess, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
