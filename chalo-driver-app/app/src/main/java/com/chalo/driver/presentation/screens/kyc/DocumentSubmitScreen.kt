package com.chalo.driver.presentation.screens.kyc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloPrimaryButton
import com.chalo.driver.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest

@Composable
fun DocumentSubmitScreen(
    onSubmitted: () -> Unit,
    viewModel: DocumentSubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                DocumentSubmitEvent.Submitted -> onSubmitted()
            }
        }
    }

    // File pickers for each document
    val licensePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onLicenseImageSelected(it) }
    }
    val rcPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onRcImageSelected(it) }
    }
    val aadharPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onAadharImageSelected(it) }
    }
    val bikePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onBikePhotoSelected(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ChaloSpacing.lg, vertical = ChaloSpacing.xl),
    ) {
        Text(
            text  = "Submit Documents",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text  = "Upload your KYC documents to start driving",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.lg))

        // License Number
        OutlinedTextField(
            value = uiState.licenseNumber,
            onValueChange = viewModel::onLicenseNumberChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Driving License Number") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        DocumentUploadButton(
            label = "Upload License Photo",
            fileName = uiState.licenseFileName,
            isUploading = uiState.isUploadingLicense,
            onClick = { licensePicker.launch("image/*") },
        )

        Spacer(Modifier.height(ChaloSpacing.md))

        // RC Number
        OutlinedTextField(
            value = uiState.rcNumber,
            onValueChange = viewModel::onRcNumberChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("RC Number") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        DocumentUploadButton(
            label = "Upload RC Photo",
            fileName = uiState.rcFileName,
            isUploading = uiState.isUploadingRc,
            onClick = { rcPicker.launch("image/*") },
        )

        Spacer(Modifier.height(ChaloSpacing.md))

        // Aadhar Number
        OutlinedTextField(
            value = uiState.aadharNumber,
            onValueChange = viewModel::onAadharNumberChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Aadhar Number (12 digits)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(ChaloSpacing.sm))
        DocumentUploadButton(
            label = "Upload Aadhar Photo",
            fileName = uiState.aadharFileName,
            isUploading = uiState.isUploadingAadhar,
            onClick = { aadharPicker.launch("image/*") },
        )

        Spacer(Modifier.height(ChaloSpacing.md))

        // Vehicle Number
        OutlinedTextField(
            value = uiState.vehicleNumber,
            onValueChange = viewModel::onVehicleNumberChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Vehicle Number (e.g., HR26AB1234)") },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(ChaloSpacing.sm))

        // Vehicle Model
        OutlinedTextField(
            value = uiState.vehicleModel,
            onValueChange = viewModel::onVehicleModelChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Vehicle Model (e.g., Honda Splendor)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.sm))
        DocumentUploadButton(
            label = "Upload Bike Photo (optional)",
            fileName = uiState.bikePhotoFileName,
            isUploading = uiState.isUploadingBikePhoto,
            onClick = { bikePicker.launch("image/*") },
        )

        Spacer(Modifier.height(ChaloSpacing.xl))

        ChaloPrimaryButton(
            text = "Submit Documents",
            onClick = viewModel::onSubmit,
            isLoading = uiState.isSubmitting,
            enabled = uiState.canSubmit,
        )

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ChaloSpacing.lg))
    }
}

@Composable
private fun DocumentUploadButton(
    label: String,
    fileName: String?,
    isUploading: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isUploading,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    ) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(ChaloSpacing.sm))
            Text("Uploading\u2026")
        } else {
            Icon(Icons.Default.Upload, contentDescription = null)
            Spacer(Modifier.width(ChaloSpacing.sm))
            Text(fileName ?: label)
        }
    }
}
