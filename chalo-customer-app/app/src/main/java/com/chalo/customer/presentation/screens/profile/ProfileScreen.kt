package com.chalo.customer.presentation.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.theme.ChaloSpacing

@Composable
fun ProfileScreen(
    onNavigateToEmergencyContact: () -> Unit,
    onNavigateToSavedLocations: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ChaloSpacing.md),
        ) {
            // Avatar + name
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(ChaloSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(60.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text  = (uiState.name?.firstOrNull()?.uppercase() ?: "C"),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(uiState.name ?: "Chalo User", style = MaterialTheme.typography.titleLarge)
                        Text(uiState.phone ?: "", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(ChaloSpacing.lg))

            // Settings items
            ProfileItem(icon = Icons.Default.ContactPhone, label = "Emergency Contact",
                onClick = onNavigateToEmergencyContact)
            ProfileItem(icon = Icons.Default.Home, label = "Saved Locations",
                onClick = onNavigateToSavedLocations)
            ProfileItem(icon = Icons.Default.Notifications, label = "Notification Preferences",
                onClick = {})
            ProfileItem(icon = Icons.Default.Help, label = "Help & Support", onClick = {})

            Spacer(Modifier.height(ChaloSpacing.lg))

            // Sign out
            OutlinedButton(
                onClick  = { showSignOutDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }

            Spacer(Modifier.height(ChaloSpacing.sm))
            Text(
                "Chalo v1.0.0 · Punjab",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out?") },
            text  = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut()
                    onSignOut()
                }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent  = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier        = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}
