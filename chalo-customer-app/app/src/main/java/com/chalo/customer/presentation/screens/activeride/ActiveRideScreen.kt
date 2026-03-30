package com.chalo.customer.presentation.screens.activeride

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.components.FullScreenError
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.*
import com.chalo.customer.util.toRupees
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ActiveRideScreen(
    rideId: String,
    onRideCompleted: (String) -> Unit,
    onPaymentRequired: (String) -> Unit,
    onNavigateHome: () -> Unit,
    onTryAgain: () -> Unit,
    viewModel: ActiveRideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(rideId) { viewModel.init(rideId) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ActiveRideEvent.RideCompleted    -> onRideCompleted(event.rideId)
                is ActiveRideEvent.PaymentRequired  -> onPaymentRequired(event.rideId)
                is ActiveRideEvent.RideNavigateHome -> onNavigateHome()
                is ActiveRideEvent.ShareUrl -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Track my Chalo ride: ${event.url}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share ride via"))
                }
                is ActiveRideEvent.SosTriggered -> { }
            }
        }
    }

    when {
        // No driver found — show dedicated screen
        uiState.noDriverFound -> NoDriverScreen(
            onTryAgain = onTryAgain,
            onGoHome   = onNavigateHome,
        )
        uiState.isLoading -> FullScreenLoading("Loading your ride...")
        uiState.errorMessage != null && uiState.ride == null ->
            FullScreenError(uiState.errorMessage!!, onRetry = viewModel::onRetry)
        else -> ActiveRideContent(
            uiState  = uiState,
            onCancel = viewModel::onCancelClick,
            onShare  = viewModel::onShareRide,
            onSos    = viewModel::onSosClick,
        )
    }

    if (uiState.showCancelSheet) {
        CancelRideSheet(
            onDismiss = viewModel::onCancelDismiss,
            onConfirm = viewModel::onConfirmCancel,
        )
    }

    if (uiState.showSosConfirm) {
        SosConfirmDialog(
            onDismiss = viewModel::onSosDismiss,
            onConfirm = { viewModel.onSosConfirm(0.0, 0.0) },
        )
    }
}

// -- No Driver Screen ----------------------------------------------------------

@Composable
fun NoDriverScreen(
    onTryAgain: () -> Unit,
    onGoHome: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier           = Modifier.size(48.dp),
                        tint               = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text      = "No Drivers Available",
                style     = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text      = "We couldn't find a driver nearby right now. This usually takes just a few minutes — please try again.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // Try again — goes back to home so user can rebook
            ChaloPrimaryButton(
                text     = "Try Again",
                onClick  = onTryAgain,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick  = onGoHome,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
            ) {
                Text("Go Home")
            }
        }
    }
}

// -- Active Ride Content -------------------------------------------------------

@Composable
private fun ActiveRideContent(
    uiState: ActiveRideUiState,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onSos: () -> Unit,
) {
    val ride = uiState.ride ?: return

    val pickupLatLng = LatLng(ride.pickupLat, ride.pickupLng)
    val driverLatLng = uiState.driverLocation ?: pickupLatLng

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(driverLatLng, 15f)
    }

    LaunchedEffect(uiState.driverLocation) {
        uiState.driverLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 15f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier            = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings          = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        ) {
            Marker(state = MarkerState(position = pickupLatLng), title = "Pickup")
            Marker(state = MarkerState(position = driverLatLng), title = "Driver", flat = true)
            ride.dropLat?.let { lat ->
                ride.dropLng?.let { lng ->
                    Marker(state = MarkerState(position = LatLng(lat, lng)), title = "Drop")
                }
            }
        }

        RideStatusCard(
            modifier = Modifier.align(Alignment.BottomCenter),
            uiState  = uiState,
            onCancel = onCancel,
            onShare  = onShare,
        )

        FloatingActionButton(
            onClick        = onSos,
            modifier       = Modifier.align(Alignment.TopEnd).padding(16.dp),
            containerColor = ChaloError,
            contentColor   = Color.White,
        ) {
            Text("SOS", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RideStatusCard(
    modifier: Modifier,
    uiState: ActiveRideUiState,
    onCancel: () -> Unit,
    onShare: () -> Unit,
) {
    val ride = uiState.ride ?: return

    Surface(
        modifier        = modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation  = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(ChaloSpacing.md)) {

            val (statusText, statusColor) = when (ride.status) {
                RideStatus.REQUESTED       -> "Searching for a driver..." to StatusSearching
                RideStatus.DRIVER_ASSIGNED -> "Driver is on the way"      to StatusAssigned
                RideStatus.DRIVER_ARRIVED  -> "Driver has arrived!"       to StatusArrived
                RideStatus.IN_PROGRESS     -> "Ride in progress"          to StatusInProgress
                else                       -> "Processing..."             to StatusSearching
            }

            Surface(
                color    = statusColor.copy(alpha = 0.15f),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text      = statusText,
                    modifier  = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style     = MaterialTheme.typography.titleSmall,
                    color     = statusColor,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(ChaloSpacing.sm))

            ride.driver?.let { driver ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(driver.name ?: "Driver", style = MaterialTheme.typography.titleMedium)
                        driver.vehicleNumber?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        driver.ratingAvg?.let {
                            Text("? ${"%.1f".format(it)}", style = MaterialTheme.typography.bodySmall,
                                color = StarColor)
                        }
                    }
                    ride.rideStartOtp?.let { otp ->
                        if (ride.status in listOf(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Column(
                                    modifier            = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("OTP", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text       = otp,
                                        style      = MaterialTheme.typography.headlineMedium,
                                        color      = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ChaloSpacing.sm))
            }

            AnimatedVisibility(ride.status == RideStatus.REQUESTED) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Finding nearby drivers...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(ChaloSpacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm)) {
                OutlinedButton(
                    onClick  = onShare,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                if (ride.status in listOf(RideStatus.REQUESTED, RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED)) {
                    OutlinedButton(
                        onClick  = onCancel,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        enabled  = !uiState.isCancelling,
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelRideSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    val reasons = listOf(
        "DRIVER_ASKED_TO_CANCEL" to "Driver asked me to cancel",
        "DRIVER_NOT_MOVING"      to "Driver not moving",
        "DRIVER_WRONG_VEHICLE"   to "Wrong vehicle",
        "DRIVER_BEHAVIOUR"       to "Driver behaviour issue",
        "CHANGED_MIND"           to "Changed my mind",
        "BOOKED_BY_MISTAKE"      to "Booked by mistake",
        "OTHER"                  to "Other reason",
    )
    var selected by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ChaloSpacing.md)
                .padding(bottom = ChaloSpacing.lg),
        ) {
            Text("Why are you cancelling?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(ChaloSpacing.md))
            reasons.forEach { (code, label) ->
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == code, onClick = { selected = code })
                    Text(label, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(ChaloSpacing.md))
            ChaloPrimaryButton(
                text    = "Confirm Cancel",
                onClick = { selected?.let { onConfirm(it, null) } },
                enabled = selected != null,
            )
        }
    }
}

@Composable
private fun SosConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon             = { Icon(Icons.Default.Warning, null, tint = ChaloError) },
        title            = { Text("Send SOS Alert?") },
        text             = { Text("This will notify your emergency contact and our safety team.") },
        confirmButton    = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = ChaloError)) {
                Text("Send SOS")
            }
        },
        dismissButton    = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}



