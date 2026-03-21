package com.chalo.customer.presentation.screens.activeride

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ActiveRideScreen(
    rideId: String,
    onRideCompleted: (String) -> Unit,
    onPaymentRequired: (String) -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: ActiveRideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(rideId) { viewModel.init(rideId) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ActiveRideEvent.RideCompleted   -> onRideCompleted(event.rideId)
                is ActiveRideEvent.PaymentRequired -> onPaymentRequired(event.rideId)
                is ActiveRideEvent.RideNavigateHome -> onNavigateHome()
                is ActiveRideEvent.ShareUrl -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Track my Chalo ride: ${event.url}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share ride via"))
                }
                is ActiveRideEvent.SosTriggered -> { /* show toast handled below */ }
            }
        }
    }

    when {
        uiState.isLoading -> FullScreenLoading("Loading your ride…")
        uiState.errorMessage != null && uiState.ride == null ->
            FullScreenError(uiState.errorMessage!!, onRetry = viewModel::onRetry)
        else -> ActiveRideContent(
            uiState    = uiState,
            onCancel   = viewModel::onCancelClick,
            onShare    = viewModel::onShareRide,
            onSos      = viewModel::onSosClick,
        )
    }

    // Cancel sheet
    if (uiState.showCancelSheet) {
        CancelRideSheet(
            onDismiss = viewModel::onCancelDismiss,
            onConfirm = viewModel::onConfirmCancel,
        )
    }

    // SOS confirm dialog
    if (uiState.showSosConfirm) {
        SosConfirmDialog(
            onDismiss = viewModel::onSosDismiss,
            onConfirm = viewModel::onSosConfirm,
        )
    }
}

@Composable
private fun ActiveRideContent(
    uiState: ActiveRideUiState,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onSos: () -> Unit,
) {
    val ride = uiState.ride ?: return

    val pickupLatLng = LatLng(ride.pickupLat, ride.pickupLng)
    val rawDriver    = uiState.driverLocation ?: pickupLatLng

    // Smooth driver marker — animate between GPS updates over 800ms
    val animLat = remember { Animatable(rawDriver.latitude.toFloat()) }
    val animLng = remember { Animatable(rawDriver.longitude.toFloat()) }

    LaunchedEffect(uiState.driverLocation) {
        uiState.driverLocation?.let { target ->
            launch { animLat.animateTo(target.latitude.toFloat(),  tween(durationMillis = 800)) }
            launch { animLng.animateTo(target.longitude.toFloat(), tween(durationMillis = 800)) }
        }
    }

    val driverLatLng = LatLng(animLat.value.toDouble(), animLng.value.toDouble())

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(driverLatLng, 15f)
    }

    // Animate camera to follow driver (uses animated position, not raw target)
    LaunchedEffect(uiState.driverLocation) {
        uiState.driverLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 15f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map
        // Decode polyline once — empty list when polyline is unavailable
        val routePoints = remember(uiState.routePolyline) {
            if (uiState.routePolyline.isNotEmpty())
                PolyUtil.decode(uiState.routePolyline)
            else
                emptyList()
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        ) {
            // Route polyline (driver → pickup → drop)
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points    = routePoints,
                    color     = androidx.compose.ui.graphics.Color(0xFF1A73E8),
                    width     = 8f,
                    geodesic  = true,
                )
            }

            // Pickup marker
            Marker(
                state = MarkerState(position = pickupLatLng),
                title = "Pickup",
            )

            // Driver marker
            Marker(
                state = MarkerState(position = driverLatLng),
                title = "Driver",
                flat  = true,
            )

            // Drop marker
            ride.dropLat?.let { dropLat ->
                ride.dropLng?.let { dropLng ->
                    Marker(
                        state = MarkerState(position = LatLng(dropLat, dropLng)),
                        title = "Drop",
                    )
                }
            }
        }

        // Status + driver info card at bottom
        RideStatusCard(
            modifier = Modifier.align(Alignment.BottomCenter),
            uiState  = uiState,
            onCancel = onCancel,
            onShare  = onShare,
        )

        // SOS button top-right
        FloatingActionButton(
            onClick        = onSos,
            modifier       = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
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
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(ChaloSpacing.md)) {

            // Status banner
            val (statusText, statusColor) = when (ride.status) {
                RideStatus.REQUESTED      -> "Searching for a driver…" to StatusSearching
                RideStatus.DRIVER_ASSIGNED -> "Driver is on the way" to StatusAssigned
                RideStatus.DRIVER_ARRIVED  -> "Driver has arrived!" to StatusArrived
                RideStatus.IN_PROGRESS    -> "Ride in progress" to StatusInProgress
                else -> "Processing…" to StatusSearching
            }

            Surface(
                color  = statusColor.copy(alpha = 0.15f),
                shape  = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text  = statusText,
                        style = MaterialTheme.typography.titleSmall,
                        color = statusColor,
                        textAlign = TextAlign.Center,
                    )
                    // Show ETA while driver is en route to pickup
                    uiState.etaMins?.let { eta ->
                        Text(
                            text  = "ETA: $eta min",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(ChaloSpacing.sm))

            // Driver info (shown after assignment)
            ride.driver?.let { driver ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(driver.name ?: "Driver", style = MaterialTheme.typography.titleMedium)
                        driver.vehicleNumber?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        driver.ratingAvg?.let {
                            Text("★ ${"%.1f".format(it)}", style = MaterialTheme.typography.bodySmall,
                                color = StarColor)
                        }
                    }

                    // OTP display
                    ride.rideStartOtp?.let { otp ->
                        if (ride.status in listOf(RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED)) {
                            Surface(
                                color  = MaterialTheme.colorScheme.primaryContainer,
                                shape  = RoundedCornerShape(8.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("OTP", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        text  = otp,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ChaloSpacing.sm))
            }

            // Searching animation (no driver yet)
            AnimatedVisibility(ride.status == RideStatus.REQUESTED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Finding nearby drivers…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(ChaloSpacing.sm))

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm)) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                // Cancel only available before ride starts
                if (ride.status in listOf(RideStatus.REQUESTED, RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isCancelling,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
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
        "DRIVER_ASKED_TO_CANCEL"   to "Driver asked me to cancel",
        "DRIVER_NOT_MOVING"        to "Driver not moving",
        "DRIVER_WRONG_VEHICLE"     to "Wrong vehicle",
        "DRIVER_BEHAVIOUR"         to "Driver behaviour issue",
        "CHANGED_MIND"             to "Changed my mind",
        "BOOKED_BY_MISTAKE"        to "Booked by mistake",
        "OTHER"                    to "Other reason",
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == code,
                        onClick  = { selected = code },
                    )
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
    onConfirm: () -> Unit,   // ViewModel fetches real GPS internally
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon     = { Icon(Icons.Default.Warning, null, tint = ChaloError) },
        title    = { Text("Send SOS Alert?") },
        text     = { Text("This will notify your emergency contact and our safety team.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = ChaloError),
            ) { Text("Send SOS") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
