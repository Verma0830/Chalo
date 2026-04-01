package com.chalo.driver.presentation.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloDangerButton
import com.chalo.driver.presentation.components.ChaloErrorScreen
import com.chalo.driver.presentation.components.ChaloInfoCard
import com.chalo.driver.presentation.components.ChaloLoadingScreen
import com.chalo.driver.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                ProfileEvent.SignedOut -> onSignedOut()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> ChaloLoadingScreen("Loading profile…")
            state.errorMessage != null && state.profile == null -> {
                ChaloErrorScreen(
                    message = state.errorMessage!!,
                    onRetry = viewModel::loadProfile,
                )
            }
            else -> {
                val profile = state.profile ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(ChaloSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(Modifier.height(ChaloSpacing.md))

                    Text(
                        text = profile.name ?: "Driver",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = profile.phone,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                    )

                    Spacer(Modifier.height(ChaloSpacing.lg))

                    // Driver details card
                    profile.driverProfile?.let { dp ->
                        ChaloInfoCard {
                            Text(
                                text = "Driver Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(ChaloSpacing.sm))

                            ProfileRow("Status", dp.verificationStatus ?: "Unknown")
                            ProfileRow("Vehicle", dp.vehicleNumber ?: "-")
                            ProfileRow("Model", dp.vehicleModel ?: "-")
                            ProfileRow("Total Rides", (dp.totalRides ?: 0).toString())

                            // Rating
                            Spacer(Modifier.height(ChaloSpacing.sm))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Rating",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = StarColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", dp.ratingAvg ?: 0.0),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = " (${dp.ratingCount ?: 0})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(ChaloSpacing.lg))

                    // Account info card
                    ChaloInfoCard {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(ChaloSpacing.sm))
                        ProfileRow("Role", profile.role)
                        ProfileRow("Email", profile.email ?: "Not set")
                    }

                    Spacer(Modifier.height(ChaloSpacing.xl))

                    // Sign out
                    ChaloDangerButton(
                        text = "Sign Out",
                        onClick = viewModel::signOut,
                        isLoading = state.isSigningOut,
                    )

                    Spacer(Modifier.height(ChaloSpacing.md))
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
