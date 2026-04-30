package com.chalo.driver.presentation.screens.ride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.domain.model.RideStatus
import com.chalo.driver.presentation.components.*
import com.chalo.driver.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ActiveRideScreen(
    rideId: String,
    onRideCompleted: (String) -> Unit,
    onRideCancelled: () -> Unit,
    viewModel: ActiveRideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) { viewModel.init(rideId) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ActiveRideEvent.Completed -> onRideCompleted(event.rideId)
                ActiveRideEvent.Cancelled -> onRideCancelled()
            }
        }
    }

    if (uiState.isLoading) {
        ChaloLoadingScreen("Loading ride\u2026")
        return
    }

    val ride = uiState.ride
    if (ride == null) {
        ChaloErrorScreen(message = uiState.errorMessage ?: "Ride not found", onRetry = { viewModel.init(rideId) })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ChaloSpacing.md),
    ) {
        // Status header
        val statusColor = when (ride.status) {
            RideStatus.DRIVER_ASSIGNED -> StatusAssigned
            RideStatus.DRIVER_ARRIVED -> StatusArrived
            RideStatus.IN_PROGRESS -> StatusInProgress
            else -> MaterialTheme.colorScheme.primary
        }
        val statusText = when (ride.status) {
            RideStatus.DRIVER_ASSIGNED -> "Navigate to Pickup"
            RideStatus.DRIVER_ARRIVED -> "Waiting for Customer"
            RideStatus.IN_PROGRESS -> "Ride in Progress"
            else -> ride.status.name
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
        ) {
            Row(
                modifier = Modifier.padding(ChaloSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (ride.status) {
                        RideStatus.DRIVER_ASSIGNED -> Icons.Default.Navigation
                        RideStatus.DRIVER_ARRIVED -> Icons.Default.HourglassTop
                        RideStatus.IN_PROGRESS -> Icons.Default.DirectionsBike
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(ChaloSpacing.sm))
                Text(statusText, style = MaterialTheme.typography.titleLarge, color = statusColor, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(ChaloSpacing.md))

        // Customer & Ride info card
        ChaloInfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = TextSecondary)
                Spacer(Modifier.width(ChaloSpacing.sm))
                Text(ride.customer?.name ?: "Passenger", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(ride.paymentMethod, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(ChaloSpacing.md))

            // Pickup
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.RadioButtonChecked, contentDescription = null, tint = ChaloSuccess, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(ChaloSpacing.sm))
                Column {
                    Text("Pickup", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(ride.pickupAddress, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(ChaloSpacing.sm))
            // Drop
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(ChaloSpacing.sm))
                Column {
                    Text("Drop-off", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(ride.dropAddress, style = MaterialTheme.typography.bodyMedium)
                }
            }

            ride.fare?.let { fare ->
                Spacer(Modifier.height(ChaloSpacing.md))
                HorizontalDivider()
                Spacer(Modifier.height(ChaloSpacing.sm))
                Text("Fare: \u20B9$fare", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(ChaloSpacing.md))

        // OTP entry (when DRIVER_ARRIVED)
        if (ride.status == RideStatus.DRIVER_ARRIVED) {
            ChaloInfoCard {
                Text("Enter Customer's OTP", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(ChaloSpacing.sm))
                Text(
                    "Ask the customer for their 4-digit ride OTP",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(ChaloSpacing.sm))
                OutlinedTextField(
                    value = uiState.otpInput,
                    onValueChange = viewModel::onOtpChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("4-digit OTP") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 8.sp,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
            Spacer(Modifier.height(ChaloSpacing.md))
        }

        Spacer(Modifier.weight(1f))

        // Error message
        uiState.errorMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(ChaloSpacing.sm))
        }

        // Action buttons based on status
        when (ride.status) {
            RideStatus.DRIVER_ASSIGNED -> {
                ChaloSuccessButton(
                    text = "I've Arrived",
                    onClick = viewModel::onArrived,
                    isLoading = uiState.isActionLoading,
                )
                Spacer(Modifier.height(ChaloSpacing.sm))
                ChaloDangerButton(
                    text = "Cancel Ride",
                    onClick = viewModel::onCancelRide,
                )
            }
            RideStatus.DRIVER_ARRIVED -> {
                ChaloSuccessButton(
                    text = "Start Ride",
                    onClick = viewModel::onStartRide,
                    isLoading = uiState.isActionLoading,
                    enabled = uiState.otpInput.length == 4,
                )
                Spacer(Modifier.height(ChaloSpacing.sm))
                ChaloDangerButton(
                    text = "Cancel Ride",
                    onClick = viewModel::onCancelRide,
                )
            }
            RideStatus.IN_PROGRESS -> {
                ChaloPrimaryButton(
                    text = "Complete Ride",
                    onClick = viewModel::onCompleteRide,
                    isLoading = uiState.isActionLoading,
                )
            }
            else -> {}
        }

        Spacer(Modifier.height(ChaloSpacing.sm))
    }
}
