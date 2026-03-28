package com.chalo.customer.presentation.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.components.FullScreenError
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.util.toReadableDate
import com.chalo.customer.util.toRupees

@Composable
fun RideDetailScreen(
    rideId: String,
    onViewReceipt: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RideDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) { viewModel.load(rideId) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Ride Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is RideDetailUiState.Loading -> FullScreenLoading()
                is RideDetailUiState.Error   -> FullScreenError(state.message, onRetry = { viewModel.load(rideId) })
                is RideDetailUiState.Success -> RideDetailContent(
                    ride           = state.ride,
                    onViewReceipt  = { onViewReceipt(rideId) },
                )
            }
        }
    }
}

@Composable
private fun RideDetailContent(ride: Ride, onViewReceipt: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ChaloSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ChaloSpacing.md),
    ) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                Text("Route", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(ChaloSpacing.sm))
                Text(ride.pickupAddress)
                Text("→ ${ride.dropAddress}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                DetailRow("Status", ride.status.name)
                DetailRow("Fare", (ride.finalFare ?: ride.estimatedFare).toRupees())
                DetailRow("Payment", ride.paymentMethod.name)
                ride.distanceKm?.let { DetailRow("Distance", "${it} km") }
                DetailRow("Date", ride.createdAt.toReadableDate())
            }
        }

        ride.driver?.let { driver ->
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                    Text("Driver", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(ChaloSpacing.sm))
                    Text(driver.name ?: "—")
                    driver.vehicleNumber?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        if (ride.status == RideStatus.COMPLETED) {
            ChaloPrimaryButton(text = "View Receipt", onClick = onViewReceipt)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
