package com.chalo.customer.presentation.screens.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.components.ChaloPrimaryButton
import com.chalo.customer.presentation.theme.ChaloSpacing
import com.chalo.customer.util.maskPhone
import com.google.android.gms.auth.api.phone.SmsRetriever
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OtpVerifyScreen(
    phone: String,
    onVerified: (isNewUser: Boolean) -> Unit,
    viewModel: OtpVerifyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(phone) { viewModel.init(phone) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is OtpVerifyEvent.Verified -> onVerified(event.isNewUser)
            }
        }
    }

    // Register SMS Retriever broadcast receiver while this screen is visible
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                    val extras    = intent.extras ?: return
                    val smsMessage = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
                    // Extract 6-digit OTP from message using regex
                    val otp = Regex("\\d{6}").find(smsMessage)?.value ?: return
                    viewModel.onSmsReceived(otp)
                }
            }
        }
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ChaloSpacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Verify OTP", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(ChaloSpacing.sm))
        Text(
            text  = "Enter the 6-digit OTP sent to ${phone.maskPhone()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ChaloSpacing.xl))

        // 6-box OTP input
        OtpInputRow(
            otp       = uiState.otp,
            onChanged = viewModel::onOtpChanged,
        )

        Spacer(Modifier.height(ChaloSpacing.lg))

        ChaloPrimaryButton(
            text      = "Verify",
            onClick   = viewModel::onVerify,
            isLoading = uiState.isLoading,
            enabled   = uiState.otp.length == 6,
        )

        Spacer(Modifier.height(ChaloSpacing.md))

        // Resend OTP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.resendCountdown > 0) {
                Text(
                    text  = "Resend in ${uiState.resendCountdown}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(onClick = viewModel::onResendOtp) {
                    Text("Resend OTP")
                }
            }
        }

        uiState.errorMessage?.let { msg ->
            Spacer(Modifier.height(ChaloSpacing.md))
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OtpInputRow(
    otp: String,
    onChanged: (String) -> Unit,
) {
    BasicTextField(
        value         = otp,
        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onChanged(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { _ ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(6) { index ->
                    val char = otp.getOrNull(index)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .border(
                                width = 2.dp,
                                color = if (otp.length == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text      = char,
                            style     = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    )
}
