package com.chalo.driver.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloPrimaryButton
import com.chalo.driver.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OtpVerifyScreen(
    phone: String,
    onVerified: (phone: String, otp: String) -> Unit,
    onBack: () -> Unit,
    viewModel: OtpVerifyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is OtpVerifyEvent.OtpVerified -> onVerified(event.phone, event.otp)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ChaloSpacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = "Verify OTP",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text  = "Enter the 6-digit code sent to $phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.xl))

        OutlinedTextField(
            value         = uiState.otpInput,
            onValueChange = { viewModel.onOtpChanged(it, phone) },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("6-digit OTP") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction    = ImeAction.Done,
            ),
            singleLine = true,
            textStyle  = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 8.sp,
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text      = "Verify",
            onClick   = { viewModel.onVerify(phone) },
            isLoading = uiState.isLoading,
            enabled   = uiState.otpInput.length == 6,
        )

        // Resend countdown
        Spacer(Modifier.height(ChaloSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (uiState.resendCooldownSecs > 0) {
                Text(
                    text  = "Resend in ${uiState.resendCooldownSecs}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(onClick = { viewModel.onResendOtp(phone) }) {
                    Text("Resend OTP")
                }
            }
        }

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(
                text  = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
