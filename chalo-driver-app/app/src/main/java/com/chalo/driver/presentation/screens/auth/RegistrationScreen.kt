package com.chalo.driver.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloPrimaryButton
import com.chalo.driver.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RegistrationScreen(
    phone: String,
    otp: String,
    onRegistered: () -> Unit,
    viewModel: RegistrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                RegistrationEvent.Success -> onRegistered()
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
            text  = "What's your name?",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text  = "This will be shown to customers during rides",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.xl))

        OutlinedTextField(
            value         = uiState.nameInput,
            onValueChange = viewModel::onNameChanged,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Full Name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text      = "Register as Driver",
            onClick   = { viewModel.onRegister(phone, otp) },
            isLoading = uiState.isLoading,
            enabled   = uiState.nameInput.length >= 2,
        )

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
