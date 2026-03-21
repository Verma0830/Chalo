package com.chalo.customer.presentation.screens.postride

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.presentation.theme.StarColor
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RatingScreen(
    rideId: String,
    onDone: () -> Unit,
    viewModel: RatingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) { viewModel.init(rideId) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                RatingEvent.Done -> onDone()
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
        Text("How was your ride?", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(ChaloSpacing.sm))

        uiState.driverName?.let { name ->
            Text("Rate your experience with $name",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(ChaloSpacing.xl))

        // Star rating
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { star ->
                Icon(
                    imageVector = if (star <= uiState.rating) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "$star star",
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { viewModel.onRatingSelected(star) },
                    tint = if (star <= uiState.rating) StarColor else MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(Modifier.height(ChaloSpacing.lg))

        OutlinedTextField(
            value         = uiState.comment,
            onValueChange = viewModel::onCommentChanged,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Add a comment (optional)") },
            minLines      = 3,
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text      = "Submit Rating",
            onClick   = viewModel::onSubmit,
            isLoading = uiState.isSubmitting,
            enabled   = uiState.rating > 0,
        )

        Spacer(Modifier.height(ChaloSpacing.sm))

        TextButton(onClick = viewModel::onRateLater) {
            Text("Rate Later")
        }

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}
