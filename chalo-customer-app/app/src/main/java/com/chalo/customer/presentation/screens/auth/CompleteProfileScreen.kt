package com.chalo.customer.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CompleteProfileScreen(
    onProfileSaved: () -> Unit,
    viewModel: CompleteProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                CompleteProfileEvent.Saved -> {
                    keyboardController?.hide()
                    onProfileSaved()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ChaloSpacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Complete your profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text  = "Tell us your name to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.xl))

        OutlinedTextField(
            value         = uiState.name,
            onValueChange = viewModel::onNameChanged,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Your name") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction      = ImeAction.Next,
            ),
            isError        = uiState.nameError != null,
            supportingText = {
                uiState.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            shape      = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.md))

        OutlinedTextField(
            value         = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Email (optional)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.onSave() }),
            singleLine = true,
            shape      = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text      = "Save & Continue",
            onClick   = viewModel::onSave,
            isLoading = uiState.isLoading,
            enabled   = uiState.name.isNotBlank(),
        )

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
