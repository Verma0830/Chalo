package com.chalo.customer.presentation.screens.home

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await

// Default map center — Ludhiana, Punjab (used only when GPS is unavailable)
private val PUNJAB_CENTER = LatLng(30.9010, 75.8573)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
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
    val context = LocalContext.current

    // --- Location permission ---
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val locationGranted = locationPermission.status.isGranted

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!locationGranted) locationPermission.launchPermissionRequest()
    }

    // --- Real pickup location (falls back to Punjab centre) ---
    var pickupLatLng by remember { mutableStateOf(PUNJAB_CENTER) }

    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            try {
                val loc = LocationServices
                    .getFusedLocationProviderClient(context)
                    .lastLocation
                    .await()
                if (loc != null) {
                    pickupLatLng = LatLng(loc.latitude, loc.longitude)
                }
            } catch (_: Exception) { /* keep Punjab fallback */ }
        }
    }

    // Navigate to active ride if one exists
    LaunchedEffect(uiState.activeRide) {
        uiState.activeRide?.let { ride -> onNavigateToActiveRide(ride.id) }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(PUNJAB_CENTER, 13f)
    }

    // Animate camera to real location when it becomes available
    LaunchedEffect(pickupLatLng) {
        if (pickupLatLng != PUNJAB_CENTER) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(pickupLatLng, 15f)
        }
    }

    var showDestinationSheet by remember { mutableStateOf(false) }

    // Show rationale if permission permanently denied
    if (!locationGranted && !locationPermission.status.shouldShowRationale) {
        // Permission permanently denied — show inline banner (does not block the app)
        LaunchedEffect(Unit) { /* Timber.w("Location permission permanently denied") */ }
    }

    Scaffold(
        bottomBar = {
            HomeBottomNav(
                onHistory       = onNavigateToHistory,
                onProfile       = onNavigateToProfile,
                onNotifications = onNavigateToNotifications,
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Full-screen map — only enable My Location layer when permission is granted
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled     = false,
                    myLocationButtonEnabled = false,
                    compassEnabled          = false,
                ),
                properties = MapProperties(isMyLocationEnabled = locationGranted),
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
                onNavigateToFareEstimate(
                    pickupLatLng.latitude, pickupLatLng.longitude, "Current Location",
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
    val context    = LocalContext.current
    var query      by remember { mutableStateOf("") }

    // Places autocomplete predictions
    var predictions by remember { mutableStateOf<List<AutocompletePredictionItem>>(emptyList()) }
    var isSearching  by remember { mutableStateOf(false) }

    // Debounced search — only call Places API after user pauses typing
    LaunchedEffect(query) {
        if (query.length < 3) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        kotlinx.coroutines.delay(300)   // 300 ms debounce
        predictions = fetchPlacesPredictions(context, query)
        isSearching = false
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
                placeholder   = { Text("Search destination in Punjab") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(ChaloSpacing.md))

            if (query.length < 3) {
                // Show popular places when query is short
                popularPlaces.forEach { place ->
                    ListItem(
                        headlineContent   = { Text(place.name) },
                        supportingContent = { Text(place.address, style = MaterialTheme.typography.bodySmall) },
                        leadingContent    = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier          = Modifier.clickable {
                            onDestinationSelected(place.lat, place.lng, place.address)
                        }
                    )
                    HorizontalDivider()
                }
            } else {
                // Show Places API results
                predictions.forEach { prediction ->
                    ListItem(
                        headlineContent   = { Text(prediction.primaryText) },
                        supportingContent = { Text(prediction.secondaryText, style = MaterialTheme.typography.bodySmall) },
                        leadingContent    = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier          = Modifier.clickable {
                            fetchPlaceDetails(context, prediction.placeId) { lat, lng, address ->
                                onDestinationSelected(lat, lng, address)
                            }
                        }
                    )
                    HorizontalDivider()
                }

                if (predictions.isEmpty() && !isSearching && query.length >= 3) {
                    Text(
                        "No results for \"$query\"",
                        modifier = Modifier.padding(ChaloSpacing.md),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Places API helpers ────────────────────────────────────────────────────────

private data class AutocompletePredictionItem(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
)

private suspend fun fetchPlacesPredictions(
    context: android.content.Context,
    query: String,
): List<AutocompletePredictionItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val client  = com.google.android.libraries.places.api.Places.createClient(context)
        val request = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
            .builder()
            .setQuery(query)
            // Bias results toward Punjab state
            .setLocationBias(
                com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                    com.google.android.gms.maps.model.LatLng(29.50, 73.85),  // SW corner
                    com.google.android.gms.maps.model.LatLng(32.60, 76.95),  // NE corner
                )
            )
            .setCountries(listOf("IN"))
            .build()

        val result = com.google.android.gms.tasks.Tasks.await(
            client.findAutocompletePredictions(request)
        )
        result.autocompletePredictions.map { p ->
            AutocompletePredictionItem(
                placeId       = p.placeId,
                primaryText   = p.getPrimaryText(null).toString(),
                secondaryText = p.getSecondaryText(null).toString(),
            )
        }
    } catch (e: Exception) {
        timber.log.Timber.w(e, "Places autocomplete error")
        emptyList()
    }
}

private fun fetchPlaceDetails(
    context: android.content.Context,
    placeId: String,
    onResult: (lat: Double, lng: Double, address: String) -> Unit,
) {
    val client  = com.google.android.libraries.places.api.Places.createClient(context)
    val fields  = listOf(
        com.google.android.libraries.places.api.model.Place.Field.LAT_LNG,
        com.google.android.libraries.places.api.model.Place.Field.ADDRESS,
    )
    val request = com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(placeId, fields)
    client.fetchPlace(request).addOnSuccessListener { response ->
        val place   = response.place
        val latLng  = place.latLng ?: return@addOnSuccessListener
        val address = place.address ?: "${ latLng.latitude }, ${ latLng.longitude }"
        onResult(latLng.latitude, latLng.longitude, address)
    }.addOnFailureListener { e ->
        timber.log.Timber.w(e, "Place detail fetch failed for $placeId")
    }
}

// ── Static popular places (shown before user types) ──────────────────────────

private data class PlaceSuggestion(val name: String, val address: String, val lat: Double, val lng: Double)

private val popularPlaces = listOf(
    PlaceSuggestion("Golden Temple", "Amritsar, Punjab", 31.6200, 74.8765),
    PlaceSuggestion("Ludhiana Bus Stand", "Ludhiana, Punjab", 30.9010, 75.8573),
    PlaceSuggestion("Chandigarh Railway Station", "Chandigarh, Punjab", 30.7333, 76.7794),
    PlaceSuggestion("Jalandhar City Railway Station", "Jalandhar, Punjab", 31.3260, 75.5762),
    PlaceSuggestion("Patiala Bus Stand", "Patiala, Punjab", 30.3398, 76.3869),
    PlaceSuggestion("Mohali Stadium", "Mohali, Punjab", 30.6942, 76.7160),
    PlaceSuggestion("Wagah Border", "Attari, Amritsar, Punjab", 31.6041, 74.5723),
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
