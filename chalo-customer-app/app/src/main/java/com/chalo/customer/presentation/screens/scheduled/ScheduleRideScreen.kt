package com.chalo.customer.presentation.screens.scheduled

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduleRideScreen(
    onRideScheduled: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ScheduleRideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ScheduleRideEvent.Scheduled -> onRideScheduled(event.rideId)
            }
        }
    }

    // Date + Time picker state
    if (uiState.showDatePicker) {
        DateTimePicker(
            onConfirm = viewModel::onDateTimeSelected,
            onDismiss = viewModel::hideDatePicker,
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Schedule Ride") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(paddingValues = padding)
                .fillMaxSize()
                .padding(all = ChaloSpacing.md),
        ) {
            Text(
                "Schedule your ride up to 7 days in advance.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            @Suppress("DEPRECATION")
            Spacer(Modifier.height(ChaloSpacing.lg))

            // Pickup address
            OutlinedTextField(
                value         = uiState.pickupAddress,
                onValueChange = viewModel::onPickupChanged,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Pickup Location") },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
            )
            @Suppress("DEPRECATION")
            Spacer(Modifier.height(ChaloSpacing.md))

            // Drop address
            OutlinedTextField(
                value         = uiState.dropAddress,
                onValueChange = viewModel::onDropChanged,
                modifier      = Modifier.fillMaxWidth(),
                label         = { Text("Destination") },
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp),
            )
            @Suppress("DEPRECATION")
            Spacer(Modifier.height(ChaloSpacing.md))

            // Scheduled time display
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = {
                        Text(uiState.scheduledAt?.let {
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it))
                        } ?: "Select date & time")
                    },
                    leadingContent = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        TextButton(onClick = viewModel::showDatePicker) {
                            Text(if (uiState.scheduledAt == null) "Select" else "Change")
                        }
                    },
                )
            }

            @Suppress("DEPRECATION")
            Spacer(Modifier.height(ChaloSpacing.md))

            // Payment method
            Text("Payment Method", style = MaterialTheme.typography.titleSmall)
            @Suppress("DEPRECATION")
            Spacer(Modifier.height(ChaloSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(ChaloSpacing.sm)) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected  = uiState.paymentMethod == method,
                        onClick   = { viewModel.onPaymentMethodSelected(method) },
                        label     = { Text(method.name) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            ChaloPrimaryButton(
                text      = "Schedule Ride",
                onClick   = viewModel::onSchedule,
                isLoading = uiState.isLoading,
                enabled   = uiState.pickupAddress.isNotBlank() &&
                        uiState.dropAddress.isNotBlank() &&
                        uiState.scheduledAt != null,
            )

            uiState.errorMessage?.let { msg ->
                @Suppress("DEPRECATION")
                Spacer(Modifier.height(ChaloSpacing.md))
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Two-step picker: first pick the date, then pick the time.
 * Combined into a single epoch-millis value on confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePicker(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 60 * 60 * 1000, // +1 hour default
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val now = System.currentTimeMillis()
                return utcTimeMillis >= (now - 24 * 60 * 60 * 1000) &&
                        utcTimeMillis <= (now + 7L * 24 * 60 * 60 * 1000)
            }
        },
    )
    val timePickerState = rememberTimePickerState(
        initialHour   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour      = false,
    )

    if (!showTimePicker) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTimePicker = true }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Time") },
            text  = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val datePart = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = datePart
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(cal.timeInMillis)
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Back") }
            },
        )
    }
}
