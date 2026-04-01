package com.chalo.driver.presentation.screens.earnings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.*
import com.chalo.driver.presentation.theme.*

@Composable
fun EarningsScreen(
    onNavigateToWithdrawal: () -> Unit,
    onNavigateToTripHistory: () -> Unit,
    onBack: () -> Unit,
    viewModel: EarningsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Earnings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (uiState.isLoading) {
            ChaloLoadingScreen()
            return
        }

        if (uiState.errorMessage != null && uiState.summary == null) {
            ChaloErrorScreen(
                message = uiState.errorMessage!!,
                onRetry = { viewModel.onPeriodChanged(uiState.selectedPeriod) },
            )
            return
        }

        LazyColumn(
            modifier = Modifier.padding(horizontal = ChaloSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
        ) {
            // Period filter tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
                ) {
                    listOf("today" to "Today", "week" to "This Week", "month" to "This Month").forEach { (period, label) ->
                        FilterChip(
                            selected = uiState.selectedPeriod == period,
                            onClick = { viewModel.onPeriodChanged(period) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(ChaloSpacing.sm))
            }

            // Summary card
            item {
                ChaloInfoCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Net Earnings", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                "\u20B9${uiState.summary?.netEarnings ?: 0}",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = ChaloSuccess,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Rides: ${uiState.summary?.totalRides ?: 0}", style = MaterialTheme.typography.bodyMedium)
                            Text("Commission: \u20B9${uiState.summary?.totalCommission ?: 0}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // Settlement card
            uiState.settlement?.let { settlement ->
                item {
                    ChaloInfoCard {
                        Text("Settlement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(ChaloSpacing.sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Available Balance", style = MaterialTheme.typography.bodyMedium)
                            Text("\u20B9${settlement.availableBalance}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending Settlement", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("\u20B9${settlement.pendingSettlement}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(ChaloSpacing.sm))
                        Button(
                            onClick = onNavigateToWithdrawal,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = settlement.availableBalance > 0,
                        ) {
                            Text("Withdraw")
                        }
                    }
                }
            }

            // Quick link to trip history
            item {
                OutlinedButton(
                    onClick = onNavigateToTripHistory,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(ChaloSpacing.sm))
                    Text("View Trip History")
                }
            }

            // Earnings list
            items(uiState.earningsList) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(ChaloSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.pickupAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(item.dropAddress, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("+\u20B9${item.earning}", style = MaterialTheme.typography.titleMedium, color = ChaloSuccess, fontWeight = FontWeight.Bold)
                            Text("-\u20B9${item.commission}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(ChaloSpacing.lg)) }
        }
    }
}
