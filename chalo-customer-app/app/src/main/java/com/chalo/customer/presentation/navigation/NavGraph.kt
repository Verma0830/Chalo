package com.chalo.customer.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chalo.customer.presentation.screens.activeride.ActiveRideScreen
import com.chalo.customer.presentation.screens.auth.CompleteProfileScreen
import com.chalo.customer.presentation.screens.auth.OtpVerifyScreen
import com.chalo.customer.presentation.screens.auth.PhoneInputScreen
import com.chalo.customer.presentation.screens.auth.SplashScreen
import com.chalo.customer.presentation.screens.home.FareEstimateScreen
import com.chalo.customer.presentation.screens.home.HomeScreen
import com.chalo.customer.presentation.screens.home.VehicleSelectScreen
import com.chalo.customer.presentation.screens.history.RideDetailScreen
import com.chalo.customer.presentation.screens.history.RideHistoryScreen
import com.chalo.customer.presentation.screens.notifications.NotificationsScreen
import com.chalo.customer.presentation.screens.postride.PaymentScreen
import com.chalo.customer.presentation.screens.postride.RatingScreen
import com.chalo.customer.presentation.screens.postride.ReceiptScreen
import com.chalo.customer.presentation.screens.profile.EmergencyContactScreen
import com.chalo.customer.presentation.screens.profile.ProfileScreen
import com.chalo.customer.presentation.screens.profile.SavedLocationsScreen
import com.chalo.customer.presentation.screens.scheduled.ScheduleRideScreen
import com.chalo.customer.presentation.screens.scheduled.ScheduledListScreen
import java.net.URLDecoder

@Composable
fun ChaloNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController    = navController,
        startDestination = Routes.SPLASH,
    ) {

        // Splash
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToHome    = { navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToPhone   = { navController.navigate(Routes.PHONE_INPUT) { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onNavigateToProfile = { navController.navigate(Routes.COMPLETE_PROFILE) { popUpTo(Routes.SPLASH) { inclusive = true } } },
            )
        }

        // Auth
        composable(Routes.PHONE_INPUT) {
            PhoneInputScreen(onOtpSent = { phone -> navController.navigate(Routes.otpVerify(phone)) })
        }

        composable(
            route     = Routes.OTP_VERIFY,
            arguments = listOf(navArgument("phone") { type = NavType.StringType }),
        ) { backStack ->
 HEAD
            val raw = backStack.arguments?.getString("phone") ?: ""

            val raw   = backStack.arguments?.getString("phone") ?: ""
 e26c940c5689544e8c47a6aa52bc74f009bac80a
            val phone = if (raw.startsWith("91")) "+$raw" else raw
            OtpVerifyScreen(
                phone      = phone,
                onVerified = { isNewUser ->
                    if (isNewUser) navController.navigate(Routes.COMPLETE_PROFILE) { popUpTo(Routes.PHONE_INPUT) { inclusive = true } }
                    else          navController.navigate(Routes.HOME)              { popUpTo(Routes.PHONE_INPUT) { inclusive = true } }
                }
            )
        }

        composable(Routes.COMPLETE_PROFILE) {
            CompleteProfileScreen(
                onProfileSaved = { navController.navigate(Routes.HOME) { popUpTo(Routes.COMPLETE_PROFILE) { inclusive = true } } }
            )
        }

        // Home � now navigates to VehicleSelect instead of FareEstimate directly
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToFareEstimate = { pLat, pLng, pAddr, dLat, dLng, dAddr ->
                    navController.navigate(Routes.vehicleSelect(pLat, pLng, pAddr, dLat, dLng, dAddr))
                },
                onNavigateToActiveRide    = { rideId -> navController.navigate(Routes.activeRide(rideId)) },
                onNavigateToHistory       = { navController.navigate(Routes.RIDE_HISTORY) },
                onNavigateToProfile       = { navController.navigate(Routes.PROFILE) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToSchedule      = { navController.navigate(Routes.SCHEDULE_RIDE) },
            )
        }

        // Vehicle Select
        composable(
            route     = Routes.VEHICLE_SELECT,
            arguments = listOf(
                navArgument("pickupLat")     { type = NavType.FloatType },
                navArgument("pickupLng")     { type = NavType.FloatType },
                navArgument("pickupAddress") { type = NavType.StringType },
                navArgument("dropLat")       { type = NavType.FloatType },
                navArgument("dropLng")       { type = NavType.FloatType },
                navArgument("dropAddress")   { type = NavType.StringType },
            )
        ) { backStack ->
            val args        = backStack.arguments!!
            val pickupLat   = args.getFloat("pickupLat").toDouble()
            val pickupLng   = args.getFloat("pickupLng").toDouble()
            val pickupAddr  = URLDecoder.decode(args.getString("pickupAddress") ?: "", "UTF-8")
            val dropLat     = args.getFloat("dropLat").toDouble()
            val dropLng     = args.getFloat("dropLng").toDouble()
            val dropAddr    = URLDecoder.decode(args.getString("dropAddress") ?: "", "UTF-8")
            VehicleSelectScreen(
                pickupLat       = pickupLat,
                pickupLng       = pickupLng,
                pickupAddress   = pickupAddr,
                dropLat         = dropLat,
                dropLng         = dropLng,
                dropAddress     = dropAddr,
                onVehicleSelected = { _ ->
                    navController.navigate(Routes.fareEstimate(pickupLat, pickupLng, pickupAddr, dropLat, dropLng, dropAddr))
                },
                onBack          = { navController.popBackStack() },
            )
        }

        // Fare Estimate
        composable(
            route     = Routes.FARE_ESTIMATE,
            arguments = listOf(
                navArgument("pickupLat")     { type = NavType.FloatType },
                navArgument("pickupLng")     { type = NavType.FloatType },
                navArgument("pickupAddress") { type = NavType.StringType },
                navArgument("dropLat")       { type = NavType.FloatType },
                navArgument("dropLng")       { type = NavType.FloatType },
                navArgument("dropAddress")   { type = NavType.StringType },
            )
        ) { backStack ->
            val args = backStack.arguments!!
            FareEstimateScreen(
                pickupLat     = args.getFloat("pickupLat").toDouble(),
                pickupLng     = args.getFloat("pickupLng").toDouble(),
                pickupAddress = URLDecoder.decode(args.getString("pickupAddress") ?: "", "UTF-8"),
                dropLat       = args.getFloat("dropLat").toDouble(),
                dropLng       = args.getFloat("dropLng").toDouble(),
                dropAddress   = URLDecoder.decode(args.getString("dropAddress") ?: "", "UTF-8"),
                onRideCreated = { rideId -> navController.navigate(Routes.activeRide(rideId)) { popUpTo(Routes.HOME) } },
                onBack        = { navController.popBackStack() },
            )
        }

        // Active Ride
        composable(
            route     = Routes.ACTIVE_RIDE,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStack ->
            val rideId = backStack.arguments?.getString("rideId") ?: ""
            ActiveRideScreen(
                rideId            = rideId,
                onRideCompleted   = { id -> navController.navigate(Routes.rating(id))  { popUpTo(Routes.HOME) } },
                onPaymentRequired = { id -> navController.navigate(Routes.payment(id)) { popUpTo(Routes.HOME) } },
                onNavigateHome    = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                onTryAgain        = { navController.popBackStack(Routes.FARE_ESTIMATE, inclusive = false) },
            )
        }

        // Post-Ride
        composable(
            route     = Routes.PAYMENT,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStack ->
            PaymentScreen(
                rideId        = backStack.arguments?.getString("rideId") ?: "",
                onPaymentDone = { id -> navController.navigate(Routes.rating(id)) { popUpTo(Routes.HOME) } },
            )
        }

        composable(
            route     = Routes.RATING,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStack ->
            RatingScreen(
                rideId = backStack.arguments?.getString("rideId") ?: "",
                onDone = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
            )
        }

        composable(
            route     = Routes.RECEIPT,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStack ->
            ReceiptScreen(
                rideId = backStack.arguments?.getString("rideId") ?: "",
                onBack = { navController.popBackStack() },
            )
        }

        // History
        composable(Routes.RIDE_HISTORY) {
            RideHistoryScreen(
                onRideClick = { id -> navController.navigate(Routes.rideDetail(id)) },
                onBack      = { navController.popBackStack() },
            )
        }

        composable(
            route     = Routes.RIDE_DETAIL,
            arguments = listOf(navArgument("rideId") { type = NavType.StringType }),
        ) { backStack ->
            RideDetailScreen(
                rideId        = backStack.arguments?.getString("rideId") ?: "",
                onViewReceipt = { id -> navController.navigate(Routes.receipt(id)) },
                onBack        = { navController.popBackStack() },
            )
        }

        // Profile
        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigateToEmergencyContact = { navController.navigate(Routes.EMERGENCY_CONTACT) },
                onNavigateToSavedLocations   = { navController.navigate(Routes.SAVED_LOCATIONS) },
                onSignOut = { navController.navigate(Routes.PHONE_INPUT) { popUpTo(0) { inclusive = true } } },
                onBack    = { navController.popBackStack() },
            )
        }

        composable(Routes.EMERGENCY_CONTACT) {
            EmergencyContactScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SAVED_LOCATIONS) {
            SavedLocationsScreen(onBack = { navController.popBackStack() })
        }

        // Notifications
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        // Scheduled
        composable(Routes.SCHEDULE_RIDE) {
            ScheduleRideScreen(
                onRideScheduled = { id -> navController.navigate(Routes.activeRide(id)) { popUpTo(Routes.HOME) } },
                onBack          = { navController.popBackStack() },
            )
        }

        composable(Routes.SCHEDULED_LIST) {
            ScheduledListScreen(
                onBack        = { navController.popBackStack() },
                onScheduleNew = { navController.navigate(Routes.SCHEDULE_RIDE) },
            )
        }
    }
}


