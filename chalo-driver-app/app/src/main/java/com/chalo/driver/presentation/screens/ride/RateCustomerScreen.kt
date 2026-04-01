package com.chalo.driver.presentation.screens.ride

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.driver.presentation.components.ChaloPrimaryButton
import com.chalo.driver.presentation.theme.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RateCustomerScreen(
    rideId: String,
    onDone: () -> Unit,
    viewModel: RateCustomerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) { viewModel.init(rideId) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                RateCustomerEvent.Done -> onDone()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ChaloSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Rate Customer",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(ChaloSpacing.md))
        Text(
            text = "How was your experience with this customer?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.xl))

        // Star rating
        Row(horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { star ->
                IconButton(onClick = { viewModel.onRatingChanged(star) }) {
                    Icon(
                        imageVector = if (star <= uiState.rating) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "$star star",
                        tint = if (star <= uiState.rating) StarColor else TextHint,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(ChaloSpacing.lg))

        // Comment field
        OutlinedTextField(
            value = uiState.comment,
            onValueChange = viewModel::onCommentChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Comment (optional)") },
            minLines = 2,
            maxLines = 4,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text = "Submit Rating",
            onClick = viewModel::onSubmit,
            isLoading = uiState.isSubmitting,
            enabled = uiState.rating > 0,
        )

        Spacer(Modifier.height(ChaloSpacing.sm))

        TextButton(onClick = { viewModel.onSkip() }) {
            Text("Skip")
        }

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
    }
}
