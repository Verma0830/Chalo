package com.chalo.customer.presentation.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.RideHistoryItem
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.presentation.components.FullScreenError
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.util.toReadableDate
import com.chalo.customer.util.toRupees

@Composable
fun RideHistoryScreen(
    onRideClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: RideHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Ride History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is RideHistoryUiState.Loading -> FullScreenLoading()
                is RideHistoryUiState.Error   -> FullScreenError(state.message, viewModel::load)
                is RideHistoryUiState.Success -> {
                    if (state.rides.isEmpty()) {
                        EmptyHistory()
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(ChaloSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
                        ) {
                            items(state.rides, key = { it.id }) { ride ->
                                RideHistoryCard(ride = ride, onClick = { onRideClick(ride.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RideHistoryCard(ride: RideHistoryItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(ChaloSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DirectionsBike, null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ride.pickupAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    text  = "→ ${ride.dropAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text  = ride.createdAt.toReadableDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text  = ride.finalFare?.toRupees() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val statusColor = when (ride.status) {
                    RideStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                    RideStatus.CANCELLED -> MaterialTheme.colorScheme.error
                    else                 -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text  = ride.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DirectionsBike, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(ChaloSpacing.md))
            Text("No rides yet", style = MaterialTheme.typography.titleMedium)
            Text("Book your first ride!", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
