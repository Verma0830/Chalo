package com.chalo.driver.presentation.screens.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloLoadingScreen
import com.chalo.driver.presentation.theme.*

@Composable
fun TripHistoryScreen(
    onBack: () -> Unit,
    viewModel: TripHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Trip History") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        if (uiState.isLoading) {
            ChaloLoadingScreen()
            return
        }

        if (uiState.trips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No trips yet", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = ChaloSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
            contentPadding = PaddingValues(vertical = ChaloSpacing.sm),
        ) {
            items(uiState.trips) { trip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(trip.status, style = MaterialTheme.typography.labelSmall,
                                color = when (trip.status) {
                                    "COMPLETED" -> ChaloSuccess
                                    "CANCELLED" -> MaterialTheme.colorScheme.error
                                    else -> TextSecondary
                                })
                            trip.completedAt?.let {
                                Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(ChaloSpacing.xs))
                        Text(trip.pickupAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(trip.dropAddress, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                        Spacer(Modifier.height(ChaloSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            trip.distanceKm?.let { Text("%.1f km".format(it), style = MaterialTheme.typography.bodySmall) }
                            trip.driverEarning?.let {
                                Text(
                                    "\u20B9$it",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ChaloSuccess,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.hasMore) {
                item {
                    TextButton(
                        onClick = viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Load More")
                    }
                }
            }
        }
    }
}
