package com.chalo.customer.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chalo.customer.presentation.theme.ChaloPrimary
import com.chalo.customer.presentation.theme.ChaloSpacing

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToPhone: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    LaunchedEffect(destination) {
        when (destination) {
            SplashDestination.HOME    -> onNavigateToHome()
            SplashDestination.PHONE   -> onNavigateToPhone()
            SplashDestination.PROFILE -> onNavigateToProfile()
            SplashDestination.LOADING -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChaloPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "Chalo",
                style = MaterialTheme.typography.displayLarge.copy(
                    color      = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 52.sp,
                )
            )
            Spacer(Modifier.height(ChaloSpacing.sm))
            Text(
                text  = "Your ride, your way",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            )
            Spacer(Modifier.height(ChaloSpacing.xxl))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
