package com.chalo.driver.presentation.screens.ride

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.*
import com.chalo.driver.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun IncomingRideScreen(
    onAccepted: (String) -> Unit,
    onDeclined: () -> Unit,
    onExpired: () -> Unit,
    viewModel: IncomingRideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is IncomingRideEvent.Accepted -> onAccepted(event.rideId)
                IncomingRideEvent.Declined -> onDeclined()
                IncomingRideEvent.Expired -> onExpired()
            }
        }
    }

    if (uiState.isLoading) {
        ChaloLoadingScreen("Loading ride request\u2026")
        return
    }

    val ride = uiState.ride
    if (ride == null) {
        ChaloErrorScreen(message = "No ride request found", onRetry = onDeclined)
        return
    }

    val timerProgress by animateFloatAsState(
        targetValue = uiState.timerSecsRemaining.toFloat() / ride.offerExpiresInSecs.toFloat(),
        label = "timer",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ChaloSpacing.md),
    ) {
        // Timer bar at top
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "New Ride Request",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(ChaloSpacing.sm))

            // Circular countdown
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { timerProgress },
                    modifier = Modifier.size(80.dp),
                    color = if (uiState.timerSecsRemaining <= 10)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "${uiState.timerSecsRemaining}s",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.timerSecsRemaining <= 10)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(ChaloSpacing.lg))

        // Ride details card
        ChaloInfoCard {
            // Pickup
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.RadioButtonChecked, contentDescription = null,
                    tint = ChaloSuccess, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(ChaloSpacing.sm))
                Column {
                    Text("Pickup", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(ride.pickup.address, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(ChaloSpacing.md))

            // Drop
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.LocationOn, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(ChaloSpacing.sm))
                Column {
                    Text("Drop-off", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text(ride.drop.address, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(ChaloSpacing.md))
            HorizontalDivider()
            Spacer(Modifier.height(ChaloSpacing.md))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ride.distanceKm?.let { km ->
                    StatItem(label = "Distance", value = "%.1f km".format(km))
                }
                ride.durationMins?.let { mins ->
                    StatItem(label = "Duration", value = "$mins min")
                }
                ride.fare?.let { fare ->
                    StatItem(label = "Fare", value = "\u20B9$fare")
                }
            }

            Spacer(Modifier.height(ChaloSpacing.md))

            // Customer info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = TextSecondary)
                Spacer(Modifier.width(ChaloSpacing.xs))
                Text(
                    text = ride.customer?.name ?: "Passenger",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(ChaloSpacing.sm))
                Text(
                    text = ride.paymentMethod,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
        ) {
            ChaloDangerButton(
                text = "Decline",
                onClick = viewModel::onDecline,
                modifier = Modifier.weight(1f),
                isLoading = uiState.isDeclining,
            )
            ChaloSuccessButton(
                text = "Accept",
                onClick = viewModel::onAccept,
                modifier = Modifier.weight(1f),
                isLoading = uiState.isAccepting,
            )
        }

        Spacer(Modifier.height(ChaloSpacing.md))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
