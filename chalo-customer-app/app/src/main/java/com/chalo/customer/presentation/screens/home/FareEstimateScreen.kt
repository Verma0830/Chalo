package com.chalo.customer.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.FareEstimate
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.components.FullScreenError
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.util.toRupees
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FareEstimateScreen(
    pickupLat: Double, pickupLng: Double, pickupAddress: String,
    dropLat: Double,   dropLng: Double,   dropAddress: String,
    onRideCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: FareEstimateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load(pickupLat, pickupLng, pickupAddress, dropLat, dropLng, dropAddress)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FareEstimateEvent.RideCreated -> onRideCreated(event.rideId)
            }
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Book Ride") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is FareEstimateUiState.Loading ->
                    FullScreenLoading("Getting fare estimate…")

                is FareEstimateUiState.Error ->
                    FullScreenError(state.message, onRetry = viewModel::onRetry)

                is FareEstimateUiState.Success ->
                    FareEstimateContent(
                        estimate        = state.estimate,
                        pickupAddress   = pickupAddress,
                        dropAddress     = dropAddress,
                        selectedPayment = state.selectedPayment,
                        isBooking       = state.isBooking,
                        onPaymentSelect = viewModel::onPaymentMethodSelected,
                        onBook          = viewModel::onBookRide,
                    )
            }
        }
    }
}

@Composable
private fun FareEstimateContent(
    estimate: FareEstimate,
    pickupAddress: String,
    dropAddress: String,
    selectedPayment: PaymentMethod,
    isBooking: Boolean,
    onPaymentSelect: (PaymentMethod) -> Unit,
    onBook: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ChaloSpacing.md),
    ) {
        Spacer(Modifier.height(ChaloSpacing.md))

        // Route card
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(pickupAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(dropAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }

        Spacer(Modifier.height(ChaloSpacing.md))

        // Fare card
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(ChaloSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Estimated Fare", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text  = estimate.estimatedFare.toRupees(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (estimate.minimumFareApplied) {
                            Text(
                                "Minimum fare applies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${estimate.distanceKm} km", style = MaterialTheme.typography.bodyMedium)
                        Text("${estimate.durationMins} min", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(ChaloSpacing.sm))
                HorizontalDivider()
                Spacer(Modifier.height(ChaloSpacing.sm))

                // Fare breakdown
                FareRow("Base Fare", estimate.baseFare.toRupees())
                FareRow("Booking Fee", estimate.bookingFee.toRupees())
                if (estimate.surgeMultiplier > 1.0) {
                    FareRow("Surge (${estimate.surgeMultiplier}×)", "Applied")
                }
            }
        }

        Spacer(Modifier.height(ChaloSpacing.md))

        // Payment method selector
        Text("Payment Method", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(ChaloSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm)) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected  = selectedPayment == method,
                    onClick   = { onPaymentSelect(method) },
                    label     = { Text(method.name) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (method == PaymentMethod.CASH) Icons.Default.Money else Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        ChaloPrimaryButton(
            text      = "Book Ride — ${estimate.estimatedFare.toRupees()}",
            onClick   = onBook,
            isLoading = isBooking,
            modifier  = Modifier.padding(bottom = ChaloSpacing.lg),
        )
    }
}

@Composable
private fun FareRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
