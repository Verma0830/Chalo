package com.chalo.customer.presentation.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing

@Composable
fun SavedLocationsScreen(
    onBack: () -> Unit,
    viewModel: SavedLocationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingType by remember { mutableStateOf<String?>(null) }
    var addressInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Saved Locations") },
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
            // Home location
            SavedLocationCard(
                type    = "Home",
                icon    = Icons.Default.Home,
                address = uiState.homeAddress,
                onEdit  = {
                    editingType  = "home"
                    addressInput = uiState.homeAddress ?: ""
                },
            )

            Spacer(Modifier.height(ChaloSpacing.md))

            // Work location
            SavedLocationCard(
                type    = "Work",
                icon    = Icons.Default.Work,
                address = uiState.workAddress,
                onEdit  = {
                    editingType  = "work"
                    addressInput = uiState.workAddress ?: ""
                },
            )
        }
    }

    // Edit dialog
    editingType?.let { type ->
        AlertDialog(
            onDismissRequest = { editingType = null },
            title = { Text("Set $type Location") },
            text  = {
                OutlinedTextField(
                    value         = addressInput,
                    onValueChange = { addressInput = it },
                    label         = { Text("Address") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine    = false,
                    minLines      = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Faridabad center as placeholder coords — replace with Places API
                    viewModel.saveLocation(type, 28.4089, 77.3178, addressInput)
                    editingType = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingType = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedLocationCard(
    type: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    address: String?,
    onEdit: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent  = { Text(type) },
            supportingContent = {
                Text(address ?: "Not set", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            leadingContent   = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent  = {
                TextButton(onClick = onEdit) { Text("Edit") }
            },
        )
    }
}
