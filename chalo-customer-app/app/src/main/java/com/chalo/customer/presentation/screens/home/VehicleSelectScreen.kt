package com.chalo.customer.presentation.screens.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chalo.customer.BuildConfig
import com.chalo.customer.R
import com.chalo.customer.domain.model.VehicleType
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

data class VehicleOption(
    val type: VehicleType,
    val displayName: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val etaMins: Int,
)

private val vehicleOptions = listOf(
    VehicleOption(VehicleType.E_RICKSHAW, "E-Rickshaw", "Eco-friendly ride",     R.drawable.ic_vehicle_erickshaw, 5),
    VehicleOption(VehicleType.AUTO,       "Auto",       "Comfortable auto ride", R.drawable.ic_vehicle_auto,      4),
    VehicleOption(VehicleType.BIKE,       "Bike",       "Quick bike rides",      R.drawable.ic_vehicle_bike,      2),
    VehicleOption(VehicleType.CAR,        "Car",        "Premium car ride",      R.drawable.ic_vehicle_car,       6),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSelectScreen(
    pickupLat: Double, pickupLng: Double, pickupAddress: String,
    dropLat: Double,   dropLng: Double,   dropAddress: String,
    onVehicleSelected: (VehicleType) -> Unit,
    onBack: () -> Unit,
) {
    val scope  = rememberCoroutineScope()
    val apiKey = BuildConfig.MAPS_API_KEY
    val pickup = LatLng(pickupLat, pickupLng)
    val drop   = LatLng(dropLat, dropLng)

    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pickup, 13f)
    }

    LaunchedEffect(Unit) {
        scope.launch {
            routePoints = fetchRoute(pickupLat, pickupLng, dropLat, dropLng, apiKey)
            val bounds = LatLngBounds.builder()
                .include(pickup)
                .include(drop)
                .build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Ride") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            GoogleMap(
                modifier            = Modifier.fillMaxWidth().weight(1f),
                cameraPositionState = cameraPositionState,
                uiSettings          = MapUiSettings(
                    zoomControlsEnabled     = false,
                    myLocationButtonEnabled = false,
                    compassEnabled          = false,
                ),
                properties = MapProperties(mapType = MapType.HYBRID),
            ) {
                if (routePoints.isNotEmpty()) {
                    Polyline(
                        points = routePoints,
                        color  = Color(0xFF1976D2),
                        width  = 10f,
                    )
                }
                Marker(
                    state = MarkerState(position = pickup),
                    title = "Pickup",
                    icon  = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                )
                Marker(
                    state = MarkerState(position = drop),
                    title = "Drop",
                    icon  = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                )
            }

            LazyColumn(
                modifier            = Modifier.fillMaxWidth().weight(1f),
                contentPadding      = PaddingValues(ChaloSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
            ) {
                items(vehicleOptions) { option ->
                    VehicleCard(option = option, onClick = { onVehicleSelected(option.type) })
                }
            }
        }
    }
}

@Composable
private fun VehicleCard(option: VehicleOption, onClick: () -> Unit) {
    Surface(
        shape           = RoundedCornerShape(16.dp),
        tonalElevation  = 2.dp,
        shadowElevation = 2.dp,
        modifier        = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier          = Modifier.padding(ChaloSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter            = painterResource(id = option.iconRes),
                        contentDescription = option.displayName,
                        modifier           = Modifier.size(40.dp),
                        tint               = Color.Unspecified,
                    )
                }
            }

            Spacer(Modifier.width(ChaloSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = option.displayName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "${option.etaMins} min",
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = "away",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
