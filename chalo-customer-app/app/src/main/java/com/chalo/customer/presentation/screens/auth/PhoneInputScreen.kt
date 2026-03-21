package com.chalo.customer.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PhoneInputScreen(
    onOtpSent: (phone: String) -> Unit,
    viewModel: PhoneInputViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // One-off events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PhoneInputEvent.OtpSent -> {
                    keyboardController?.hide()
                    onOtpSent(event.phone)
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ChaloSpacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = "Enter your\nphone number",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text  = "We'll send you an OTP to verify",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.xl))

        OutlinedTextField(
            value         = uiState.phoneInput,
            onValueChange = viewModel::onPhoneChanged,
            modifier      = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label         = { Text("+91 XXXXX XXXXX") },
            prefix        = { Text("+91  ") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { viewModel.onSendOtp() }
            ),
            isError         = uiState.phoneError != null,
            supportingText  = {
                uiState.phoneError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            shape      = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text      = "Send OTP",
            onClick   = viewModel::onSendOtp,
            isLoading = uiState.isLoading,
            enabled   = uiState.phoneInput.isNotBlank(),
        )

        // Error snackbar
        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(
                text  = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
