package com.chalo.customer.presentation.screens.home

import androidx.compose.foundation.clickable
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
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

// Default map center — Faridabad, Haryana
private val FARIDABAD_CENTER = LatLng(28.4089, 77.3178)

@Composable
fun HomeScreen(
    onNavigateToFareEstimate: (Double, Double, String, Double, Double, String) -> Unit,
    onNavigateToActiveRide: (String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate to active ride if one exists
    LaunchedEffect(uiState.activeRide) {
        uiState.activeRide?.let { ride ->
            onNavigateToActiveRide(ride.id)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(FARIDABAD_CENTER, 13f)
    }

    var destinationInput by remember { mutableStateOf("") }
    var showDestinationSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            HomeBottomNav(
                onHistory      = onNavigateToHistory,
                onProfile      = onNavigateToProfile,
                onNotifications = onNavigateToNotifications,
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Full-screen map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled    = false,
                    myLocationButtonEnabled = false,
                    compassEnabled         = false,
                ),
                properties = MapProperties(isMyLocationEnabled = true),
            )

            // Top search bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ChaloSpacing.md)
                    .align(Alignment.TopCenter),
            ) {
                // Greeting chip
                uiState.userName?.let { name ->
                    Surface(
                        shape  = RoundedCornerShape(20.dp),
                        color  = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(
                            text     = "Hey, $name!",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // Destination search card
                Surface(
                    shape   = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDestinationSheet = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text  = "Where to?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Schedule ride FAB
            FloatingActionButton(
                onClick   = onNavigateToSchedule,
                modifier  = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.secondary,
            ) {
                Icon(Icons.Default.Schedule, contentDescription = "Schedule Ride", tint = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }

    // Destination picker bottom sheet
    if (showDestinationSheet) {
        DestinationPickerSheet(
            onDismiss = { showDestinationSheet = false },
            onDestinationSelected = { dropLat, dropLng, dropAddr ->
                showDestinationSheet = false
                // Use Faridabad center as pickup placeholder
                // In production, use FusedLocationProvider for real pickup
                onNavigateToFareEstimate(
                    FARIDABAD_CENTER.latitude, FARIDABAD_CENTER.longitude, "Current Location",
                    dropLat, dropLng, dropAddr,
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationPickerSheet(
    onDismiss: () -> Unit,
    onDestinationSelected: (Double, Double, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    // Hardcoded popular spots for Punjab (expand in V2 with Places API)
    val suggestions = remember(query) {
        if (query.isBlank()) popularPlaces
        else popularPlaces.filter { it.name.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChaloSpacing.md)
                .padding(bottom = ChaloSpacing.lg),
        ) {
            Text("Where to?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(ChaloSpacing.md))

            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Search destination") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(ChaloSpacing.md))

            suggestions.forEach { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = { Text(place.address, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onDestinationSelected(place.lat, place.lng, place.address)
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

private data class PlaceSuggestion(val name: String, val address: String, val lat: Double, val lng: Double)

private val popularPlaces = listOf(
    PlaceSuggestion("Old Faridabad Railway Station", "Old Faridabad, Haryana", 28.4118, 77.3120),
    PlaceSuggestion("Badkhal Lake", "Badkhal, Faridabad, Haryana", 28.3890, 77.3040),
    PlaceSuggestion("Sector 12 Faridabad", "Sector 12, Faridabad, Haryana", 28.4001, 77.3250),
    PlaceSuggestion("Bata Chowk", "NIT, Faridabad, Haryana", 28.3898, 77.3178),
    PlaceSuggestion("Faridabad Bus Stand", "Faridabad, Haryana", 28.4068, 77.3086),
    PlaceSuggestion("Crown Plaza", "Sector 15A, Faridabad, Haryana", 28.4200, 77.3300),
    PlaceSuggestion("Escorts Mujesar Metro", "Mujesar, Faridabad, Haryana", 28.3790, 77.3130),
)

@Composable
private fun HomeBottomNav(
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onNotifications: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick  = {},
            icon     = { Icon(Icons.Default.Home, null) },
            label    = { Text("Home") },
        )
        NavigationBarItem(
            selected = false,
            onClick  = onHistory,
            icon     = { Icon(Icons.Default.History, null) },
            label    = { Text("Rides") },
        )
        NavigationBarItem(
            selected = false,
            onClick  = onNotifications,
            icon     = { Icon(Icons.Default.Notifications, null) },
            label    = { Text("Alerts") },
        )
        NavigationBarItem(
            selected = false,
            onClick  = onProfile,
            icon     = { Icon(Icons.Default.Person, null) },
            label    = { Text("Profile") },
        )
    }
}
