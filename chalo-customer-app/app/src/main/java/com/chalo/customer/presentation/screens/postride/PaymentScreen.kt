package com.chalo.customer.presentation.screens.postride

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.components.FullScreenLoading
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.util.toRupees
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PaymentScreen(
    rideId: String,
    onPaymentDone: (String) -> Unit,
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(rideId) { viewModel.init(rideId) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PaymentEvent.PaymentComplete -> onPaymentDone(event.rideId)
            }
        }
    }

    when {
        uiState.isLoading -> FullScreenLoading("Loading payment details…")
        else              -> PaymentContent(
            uiState   = uiState,
            onPayNow  = viewModel::onPayNow,
        )
    }
}

@Composable
private fun PaymentContent(
    uiState: PaymentUiState,
    onPayNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ChaloSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector  = Icons.Default.CreditCard,
            contentDescription = null,
            modifier     = Modifier.size(64.dp),
            tint         = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(ChaloSpacing.md))
        Text("Payment Pending", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text      = "Please complete your UPI payment",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(ChaloSpacing.xl))

        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ChaloSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Amount Due", style = MaterialTheme.typography.titleMedium)
                Text(
                    text  = uiState.amountPaise.div(100).toRupees(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(ChaloSpacing.xl))

        // NOTE: In V1, Razorpay SDK opens here.
        // Razorpay requires Activity reference — handled via MainActivity callback.
        // The onPayNow triggers the order creation; Razorpay opens from Activity.
        ChaloPrimaryButton(
            text      = "Pay via UPI",
            onClick   = onPayNow,
            isLoading = uiState.isProcessing,
        )

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}
