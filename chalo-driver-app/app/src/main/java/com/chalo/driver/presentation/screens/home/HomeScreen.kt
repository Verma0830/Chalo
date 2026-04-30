package com.chalo.driver.presentation.screens.home

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.chalo.driver.presentation.components.ChaloInfoCard
import com.chalo.driver.presentation.components.ChaloLoadingScreen
import com.chalo.driver.presentation.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onNavigateToIncomingRide: () -> Unit,
    onNavigateToActiveRide: (String) -> Unit,
    onNavigateToEarnings: () -> Unit,
    onNavigateToTripHistory: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )

    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.IncomingRide -> onNavigateToIncomingRide()
                is HomeEvent.ActiveRide -> onNavigateToActiveRide(event.rideId)
            }
        }
    }

    if (uiState.isLoading) {
        ChaloLoadingScreen()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = {
                Text("Chalo Driver", style = MaterialTheme.typography.titleLarge)
            },
            actions = {
                IconButton(onClick = onNavigateToProfile) {
                    Icon(Icons.Default.Person, contentDescription = "Profile")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ChaloSpacing.md),
        ) {
            Spacer(Modifier.height(ChaloSpacing.md))

            // Online/Offline Toggle Card
            ChaloInfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = if (uiState.isOnline) "You are Online" else "You are Offline",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (uiState.isOnline) "Waiting for ride requests\u2026"
                                   else "Go online to receive rides",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Toggle button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isOnline) StatusOnline else StatusOffline),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.isTogglingOnline) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.toggleOnline(locationPermissions.allPermissionsGranted) }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isOnline) Icons.Default.PowerSettingsNew
                                                  else Icons.Default.PowerSettingsNew,
                                    contentDescription = "Toggle online",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(ChaloSpacing.md))

            // Active ride card (if any)
            uiState.activeRide?.let { ride ->
                ChaloInfoCard {
                    Text(
                        text = "Active Ride",
                        style = MaterialTheme.typography.titleMedium,
                        color = StatusInProgress,
                    )
                    Spacer(Modifier.height(ChaloSpacing.sm))
                    Text("Pickup: ${ride.pickupAddress}", style = MaterialTheme.typography.bodyMedium)
                    Text("Drop: ${ride.dropAddress}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(ChaloSpacing.sm))
                    Button(
                        onClick = { onNavigateToActiveRide(ride.rideId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("View Ride")
                    }
                }
                Spacer(Modifier.height(ChaloSpacing.md))
            }

            // Quick stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
            ) {
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Today's Rides",
                    value = "${uiState.todayRides}",
                    icon = Icons.Default.DirectionsBike,
                )
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    label = "Today's Earnings",
                    value = "\u20B9${uiState.todayEarnings}",
                    icon = Icons.Default.CurrencyRupee,
                )
            }

            Spacer(Modifier.height(ChaloSpacing.md))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = onNavigateToEarnings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ChaloSpacing.xs))
                    Text("Earnings")
                }
                OutlinedButton(
                    onClick = onNavigateToTripHistory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ChaloSpacing.xs))
                    Text("Trips")
                }
            }

            // Error message
            uiState.errorMessage?.let { msg ->
                Spacer(Modifier.height(ChaloSpacing.md))
                Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(ChaloSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(ChaloSpacing.xs))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
