package com.chalo.driver.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chalo.driver.presentation.screens.auth.OtpVerifyScreen
import com.chalo.driver.presentation.screens.auth.PhoneInputScreen
import com.chalo.driver.presentation.screens.auth.RegistrationScreen
import com.chalo.driver.presentation.screens.auth.SplashScreen
import com.chalo.driver.presentation.screens.earnings.EarningsScreen
import com.chalo.driver.presentation.screens.earnings.TripHistoryScreen
import com.chalo.driver.presentation.screens.earnings.WithdrawalScreen
import com.chalo.driver.presentation.screens.home.HomeScreen
import com.chalo.driver.presentation.screens.kyc.DocumentSubmitScreen
import com.chalo.driver.presentation.screens.kyc.KycPendingScreen
import com.chalo.driver.presentation.screens.profile.ProfileScreen
import com.chalo.driver.presentation.screens.ride.ActiveRideScreen
import com.chalo.driver.presentation.screens.ride.IncomingRideScreen
import com.chalo.driver.presentation.screens.ride.RateCustomerScreen

@Composable
fun DriverNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        // ── Auth ─────────────────────────────────────────────────

        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Routes.PHONE_INPUT) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToKyc = {
                    navController.navigate(Routes.DOCUMENT_SUBMIT) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToKycPending = {
                    navController.navigate(Routes.KYC_PENDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.PHONE_INPUT) {
            PhoneInputScreen(
                onOtpSent = { phone ->
                    navController.navigate(Routes.otpVerify(phone))
                },
            )
        }

        composable(
            route = Routes.OTP_VERIFY,
            arguments = listOf(navArgument("phone") { type = NavType.StringType }),
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            OtpVerifyScreen(
                phone = "+91$phone",
                onVerified = { verifiedPhone, otp ->
                    navController.navigate(Routes.registration(verifiedPhone, otp)) {
                        popUpTo(Routes.PHONE_INPUT)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.REGISTRATION,
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType },
                navArgument("otp") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            val otp = backStackEntry.arguments?.getString("otp") ?: ""
            RegistrationScreen(
                phone = "+91$phone",
                otp = otp,
                onRegistered = {
                    navController.navigate(Routes.DOCUMENT_SUBMIT) {
                        popUpTo(Routes.PHONE_INPUT) { inclusive = true }
                    }
                },
            )
        }

        // ── KYC ──────────────────────────────────────────────────

        composable(Routes.DOCUMENT_SUBMIT) {
            DocumentSubmitScreen(
                onSubmitted = {
                    navController.navigate(Routes.KYC_PENDING) {
                        popUpTo(Routes.DOCUMENT_SUBMIT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.KYC_PENDING) {
            KycPendingScreen(
                onVerified = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.KYC_PENDING) { inclusive = true }
                    }
                },
                onResubmit = {
                    navController.navigate(Routes.DOCUMENT_SUBMIT) {
                        popUpTo(Routes.KYC_PENDING) { inclusive = true }
                    }
                },
            )
        }

        // ── Main / Home ──────────────────────────────────────────

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToIncomingRide = {
                    navController.navigate(Routes.INCOMING_RIDE)
                },
                onNavigateToActiveRide = { rideId ->
                    navController.navigate(Routes.activeRide(rideId))
                },
                onNavigateToEarnings = {
                    navController.navigate(Routes.EARNINGS)
                },
                onNavigateToTripHistory = {
                    navController.navigate(Routes.TRIP_HISTORY)
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                },
            )
        }

        // ── Ride flow ────────────────────────────────────────────

        composable(Routes.INCOMING_RIDE) {
            IncomingRideScreen(
                onAccepted = { rideId ->
                    navController.navigate(Routes.activeRide(rideId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onDeclined = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onExpired = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = Routes.ACTIVE_RIDE,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val rideId = backStackEntry.arguments?.getString("rideId") ?: ""
            ActiveRideScreen(
                rideId = rideId,
                onRideCompleted = { completedRideId ->
                    navController.navigate(Routes.rateCustomer(completedRideId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onRideCancelled = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = Routes.RATE_CUSTOMER,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val rideId = backStackEntry.arguments?.getString("rideId") ?: ""
            RateCustomerScreen(
                rideId = rideId,
                onDone = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        // ── Earnings & History ───────────────────────────────────

        composable(Routes.EARNINGS) {
            EarningsScreen(
                onNavigateToWithdrawal = {
                    navController.navigate(Routes.WITHDRAWAL)
                },
                onNavigateToTripHistory = {
                    navController.navigate(Routes.TRIP_HISTORY)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TRIP_HISTORY) {
            TripHistoryScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.WITHDRAWAL) {
            WithdrawalScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        // ── Profile ──────────────────────────────────────────────

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.PHONE_INPUT) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
