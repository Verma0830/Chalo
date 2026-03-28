package com.chalo.customer.presentation.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.BuildConfig
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

private val FARIDABAD_CENTER = LatLng(28.4089, 77.3178)

data class PlaceSuggestion(
    val placeId: String,
    val mainText: String,
    val secondaryText: String,
)

data class PlaceDetails(
    val lat: Double,
    val lng: Double,
    val name: String,
    val address: String,
)

suspend fun searchPlaces(
    query: String,
    apiKey: String,
    lat: Double = 28.4089,
    lng: Double = 77.3178,
): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
    try {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
            "?input=$encoded" +
            "&key=$apiKey" +
            "&components=country:in" +
            "&language=en" +
            "" +
            "&location=$lat,$lng" +
            "&radius=100000"
        val response = URL(url).openConnection().apply {
            connectTimeout = 5000
            readTimeout = 5000
        }.getInputStream().bufferedReader().readText()
        val json = JSONObject(response)
        val predictions = json.getJSONArray("predictions")
        (0 until predictions.length()).map { i ->
            val p = predictions.getJSONObject(i)
            val sf = p.getJSONObject("structured_formatting")
            PlaceSuggestion(
                placeId       = p.getString("place_id"),
                mainText      = sf.getString("main_text"),
                secondaryText = sf.optString("secondary_text", ""),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

suspend fun getPlaceDetails(placeId: String, apiKey: String): PlaceDetails? =
    withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=$placeId" +
                "&fields=geometry,name,formatted_address" +
                "&key=$apiKey"
            val response = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }.getInputStream().bufferedReader().readText()
            val json = JSONObject(response)
            val result = json.getJSONObject("result")
            val location = result.getJSONObject("geometry").getJSONObject("location")
            PlaceDetails(
                lat     = location.getDouble("lat"),
                lng     = location.getDouble("lng"),
                name    = result.optString("name", ""),
                address = result.optString("formatted_address", ""),
            )
        } catch (e: Exception) {
            null
        }
    }

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()
    val apiKey = BuildConfig.MAPS_API_KEY

    LaunchedEffect(uiState.activeRide) {
        uiState.activeRide?.let { ride -> onNavigateToActiveRide(ride.id) }
    }

    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var userLat by remember { mutableStateOf(FARIDABAD_CENTER.latitude) }
    var userLng by remember { mutableStateOf(FARIDABAD_CENTER.longitude) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(FARIDABAD_CENTER, 13f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!locationGranted) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ))
        }
    }

    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            LocationServices.getFusedLocationProviderClient(context)
                .lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        userLat = it.latitude
                        userLng = it.longitude
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                        )
                    }
                }
        }
    }

    var showDestinationSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        isSearching = true
        suggestions = searchPlaces(searchQuery, apiKey, userLat, userLng)
        isSearching = false
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
        Box(modifier = Modifier.padding(paddingValues = padding).fillMaxSize()) {

            GoogleMap(
                modifier            = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings          = MapUiSettings(
                    zoomControlsEnabled     = false,
                    myLocationButtonEnabled = false,
                    compassEnabled          = false,
                ),
                properties = MapProperties(
                    isMyLocationEnabled = locationGranted,
                    mapType             = MapType.HYBRID,
                ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = ChaloSpacing.md)
                    .align(Alignment.TopCenter),
            ) {
                uiState.userName?.let { name ->
                    Surface(
                        shape    = RoundedCornerShape(20.dp),
                        color    = MaterialTheme.colorScheme.primaryContainer,
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

                Surface(
                    shape           = RoundedCornerShape(16.dp),
                    tonalElevation  = 4.dp,
                    shadowElevation = 4.dp,
                    modifier        = Modifier
                        .fillMaxWidth()
                        .clickable { showDestinationSheet = true },
                ) {
                    Row(
                        modifier          = Modifier.padding(all = ChaloSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Search,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(ChaloSpacing.sm))
                        Text(
                            text  = "Where to?",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier            = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(all = ChaloSpacing.md),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButton(
                    onClick        = { onNavigateToSchedule() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier       = Modifier.padding(bottom = ChaloSpacing.sm),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = "Schedule Ride")
                }

                FloatingActionButton(
                    onClick = {
                        if (locationGranted) {
                            LocationServices.getFusedLocationProviderClient(context)
                                .lastLocation.addOnSuccessListener { location ->
                                    location?.let {
                                        userLat = it.latitude
                                        userLng = it.longitude
                                        cameraPositionState.move(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                                        )
                                    }
                                }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My Location")
                }
            }
        }
    }

    if (showDestinationSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showDestinationSheet = false
                searchQuery = ""
                suggestions = emptyList()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChaloSpacing.md)
                    .padding(bottom = ChaloSpacing.lg),
            ) {
                Text(
                    text     = "Where to?",
                    style    = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = ChaloSpacing.md),
                )

                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("Search destination...") },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon  = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape      = RoundedCornerShape(12.dp),
                )

                Spacer(modifier = Modifier.height(ChaloSpacing.sm))

                if (isSearching || isNavigating) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(ChaloSpacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                LazyColumn {
                    items(suggestions) { suggestion ->
                        ListItem(
                            headlineContent   = { Text(suggestion.mainText) },
                            supportingContent = {
                                if (suggestion.secondaryText.isNotEmpty()) {
                                    Text(
                                        suggestion.secondaryText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier = Modifier.clickable {
                                if (isNavigating) return@clickable
                                isNavigating = true
                                scope.launch {
                                    val details = getPlaceDetails(suggestion.placeId, apiKey)
                                    if (details != null) {
                                        LocationServices.getFusedLocationProviderClient(context)
                                            .lastLocation.addOnSuccessListener { location ->
                                                scope.launch {
                                                    val pickupLat = location?.latitude ?: userLat
                                                    val pickupLng = location?.longitude ?: userLng
                                                    val pickupAddress = if (location != null) {
                                                        withContext(Dispatchers.IO) {
                                                            try {
                                                                val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                                                                    "?latlng=$pickupLat,$pickupLng" +
                                                                    "&key=$apiKey" +
                                                                    "&language=en"
                                                                val resp = URL(url).openConnection().apply {
                                                                    connectTimeout = 5000
                                                                    readTimeout = 5000
                                                                }.getInputStream().bufferedReader().readText()
                                                                JSONObject(resp)
                                                                    .getJSONArray("results")
                                                                    .optJSONObject(0)
                                                                    ?.optString("formatted_address", "Current Location")
                                                                    ?: "Current Location"
                                                            } catch (e: Exception) {
                                                                "Current Location"
                                                            }
                                                        }
                                                    } else "Current Location"

                                                    showDestinationSheet = false
                                                    searchQuery = ""
                                                    suggestions = emptyList()
                                                    isNavigating = false

                                                    onNavigateToFareEstimate(
                                                        pickupLat,
                                                        pickupLng,
                                                        pickupAddress,
                                                        details.lat,
                                                        details.lng,
                                                        "${suggestion.mainText}, ${suggestion.secondaryText}",
                                                    )
                                                }
                                            }
                                    } else {
                                        isNavigating = false
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun HomeBottomNav(
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onNotifications: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Home, contentDescription = null) },
            label    = { Text("Home") },
            selected = true,
            onClick  = { }
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.History, contentDescription = null) },
            label    = { Text("History") },
            selected = false,
            onClick  = onHistory
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Notifications, contentDescription = null) },
            label    = { Text("Alerts") },
            selected = false,
            onClick  = onNotifications
        )
        NavigationBarItem(
            icon     = { Icon(Icons.Default.Person, contentDescription = null) },
            label    = { Text("Profile") },
            selected = false,
            onClick  = onProfile
        )
    }
}

