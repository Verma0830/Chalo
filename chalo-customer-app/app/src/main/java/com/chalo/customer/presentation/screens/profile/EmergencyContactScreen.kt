package com.chalo.customer.presentation.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EmergencyContactScreen(
    onBack: () -> Unit,
    viewModel: EmergencyContactViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                EmergencyContactEvent.Saved -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Emergency Contact") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(ChaloSpacing.md),
        ) {
            Text(
                "This contact will be notified if you trigger SOS during a ride.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ChaloSpacing.lg))

            OutlinedTextField(
                value         = uiState.name,
                onValueChange = viewModel::onNameChanged,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Contact Name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError        = uiState.nameError != null,
                supportingText = { uiState.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(ChaloSpacing.md))

            OutlinedTextField(
                value         = uiState.phone,
                onValueChange = viewModel::onPhoneChanged,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Contact Phone (+91XXXXXXXXXX)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError        = uiState.phoneError != null,
                supportingText = { uiState.phoneError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                shape      = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(ChaloSpacing.lg))

            ChaloPrimaryButton(
                text      = "Save Contact",
                onClick   = viewModel::onSave,
                isLoading = uiState.isLoading,
                enabled   = uiState.name.isNotBlank() && uiState.phone.isNotBlank(),
            )

            uiState.errorMessage?.let { msg ->
                Spacer(Modifier.height(ChaloSpacing.md))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
