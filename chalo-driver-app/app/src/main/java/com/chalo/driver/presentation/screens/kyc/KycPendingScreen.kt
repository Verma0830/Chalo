package com.chalo.driver.presentation.screens.kyc

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.domain.model.VerificationStatus
import com.chalo.driver.presentation.theme.ChaloSpacing
import com.chalo.driver.presentation.theme.ChaloSuccess
import kotlinx.coroutines.flow.collectLatest

@Composable
fun KycPendingScreen(
    onVerified: () -> Unit,
    onResubmit: () -> Unit,
    viewModel: KycPendingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                KycPendingEvent.Verified -> onVerified()
                KycPendingEvent.Rejected -> onResubmit()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ChaloSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState.status) {
                VerificationStatus.UNDER_REVIEW -> {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(ChaloSpacing.lg))
                    Text(
                        text = "Documents Under Review",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(ChaloSpacing.md))
                    Text(
                        text = "Your documents are being reviewed by our team. This usually takes a few hours. We'll notify you once approved.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ChaloSpacing.xl))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(ChaloSpacing.md))
                    Text(
                        text = "Checking status\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VerificationStatus.VERIFIED -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = ChaloSuccess,
                    )
                    Spacer(Modifier.height(ChaloSpacing.lg))
                    Text(
                        text = "Verified!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(ChaloSpacing.sm))
                    Text(
                        text = "Your account is approved. Redirecting\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VerificationStatus.REJECTED -> {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(ChaloSpacing.lg))
                    Text(
                        text = "Documents Rejected",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(ChaloSpacing.md))
                    Text(
                        text = "Please re-submit your documents with correct information.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ChaloSpacing.xl))
                    Button(onClick = onResubmit) {
                        Text("Re-submit Documents")
                    }
                }
                VerificationStatus.UNVERIFIED -> {
                    Text(
                        text = "Please submit your documents first.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(ChaloSpacing.md))
                    Button(onClick = onResubmit) {
                        Text("Submit Documents")
                    }
                }
            }

            uiState.errorMessage?.let { msg ->
                Spacer(Modifier.height(ChaloSpacing.md))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
