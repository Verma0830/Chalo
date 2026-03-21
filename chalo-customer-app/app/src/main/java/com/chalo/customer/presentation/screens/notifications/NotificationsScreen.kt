package com.chalo.customer.presentation.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.AppNotification
import com.chalo.customer.presentation.components.FullScreenError
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.util.toReadableDate

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            is NotificationsUiState.Loading -> FullScreenLoading()
            is NotificationsUiState.Error   -> FullScreenError(state.message, viewModel::load)
            is NotificationsUiState.Success -> {
                if (state.notifications.isEmpty()) {
                    NotificationsEmptyState(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(ChaloSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(ChaloSpacing.sm),
                    ) {
                        items(state.notifications, key = { it.id }) { notification ->
                            NotificationCard(
                                notification = notification,
                                onRead = { viewModel.markRead(notification.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: AppNotification,
    onRead: () -> Unit,
) {
    val containerColor = if (notification.isRead)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = if (notification.isRead) 1.dp else 2.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!notification.isRead) onRead() },
    ) {
        Column(modifier = Modifier.padding(ChaloSpacing.md)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!notification.isRead) {
                    Spacer(Modifier.width(ChaloSpacing.sm))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp),
                    ) {}
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                notification.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ChaloSpacing.xs))
            Text(
                notification.createdAt.toReadableDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun NotificationsEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsNone,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(
                "No notifications yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
