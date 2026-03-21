package com.chalo.customer.presentation.screens.postride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.RideReceipt
import com.chalo.customer.presentation.components.FullScreenError
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.presentation.theme.ChaloSuccess
import com.chalo.customer.util.toReadableDate
import com.chalo.customer.util.toRupees

@Composable
fun ReceiptScreen(
    rideId: String,
    onBack: () -> Unit,
    viewModel: ReceiptViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) { viewModel.load(rideId) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Receipt") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is ReceiptUiState.Loading -> FullScreenLoading()
                is ReceiptUiState.Error   -> FullScreenError(state.message, onRetry = { viewModel.load(rideId) })
                is ReceiptUiState.Success -> ReceiptContent(state.receipt)
            }
        }
    }
}

@Composable
private fun ReceiptContent(receipt: RideReceipt) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ChaloSpacing.md),
    ) {
        // Success icon
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = ChaloSuccess)
            Spacer(Modifier.height(ChaloSpacing.sm))
            Text("Ride Complete!", style = MaterialTheme.typography.headlineMedium)
            Text(receipt.completedAt.toReadableDate(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(ChaloSpacing.lg))

        // Route
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                Text("Route", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ChaloSpacing.sm))
                Text(receipt.pickupAddress, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("↓", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(receipt.dropAddress, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(ChaloSpacing.sm))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${receipt.distanceKm} km", style = MaterialTheme.typography.bodySmall)
                    Text("${receipt.durationMins} min", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(ChaloSpacing.md))

        // Fare breakdown
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                Text("Fare Breakdown", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ChaloSpacing.sm))
                ReceiptRow("Base Fare", receipt.baseFare.toRupees())
                ReceiptRow("Booking Fee", receipt.bookingFee.toRupees())
                if (receipt.surgeMultiplier > 1.0) {
                    ReceiptRow("Surge (${receipt.surgeMultiplier}×)", "Applied")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = ChaloSpacing.sm))
                ReceiptRow("Total", receipt.finalFare.toRupees(), isBold = true)
                Spacer(Modifier.height(ChaloSpacing.sm))
                ReceiptRow("Payment", receipt.paymentMethod, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReceiptRow("Status", receipt.paymentStatus, color = if (receipt.paymentStatus == "COMPLETED") ChaloSuccess else MaterialTheme.colorScheme.error)
            }
        }

        // Driver info
        receipt.driver?.let { driver ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                    Text("Driver", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(ChaloSpacing.sm))
                    Text(driver.name ?: "—", style = MaterialTheme.typography.bodyMedium)
                    driver.vehicleNumber?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String, value: String, isBold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = if (isBold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (isBold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = color)
    }
}
