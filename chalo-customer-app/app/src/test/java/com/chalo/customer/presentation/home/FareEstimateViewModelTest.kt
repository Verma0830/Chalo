package com.chalo.customer.presentation.home

import app.cash.turbine.test
import com.chalo.customer.domain.model.FareEstimate
import com.chalo.customer.domain.model.Location
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.repository.RideRepository
import com.chalo.customer.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FareEstimateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rideRepository: RideRepository = mockk()
    private lateinit var viewModel: FareEstimateViewModel

    private val pickup = Location(28.4089, 77.3178, "Sector 14, Faridabad")
    private val drop   = Location(28.3852, 77.3126, "NIT Faridabad")

    private val fakeEstimate = FareEstimate(
        estimatedFare      = 85,
        distanceKm         = 3.2,
        durationMins       = 12,
        baseFare           = 50,
        bookingFee         = 10,
        surgeMultiplier    = 1.0,
        minimumFareApplied = false,
        minimumFare        = 40,
    )

    @Before
    fun setUp() {
        viewModel = FareEstimateViewModel(rideRepository)
    }

    // ── Initial state ─────────────────────────────────────────────

    @Test
    fun `initial state is Loading`() {
        assertTrue(viewModel.uiState.value is FareEstimateUiState.Loading)
    }

    // ── load() and fare estimate ──────────────────────────────────

    @Test
    fun `load fetches estimate and emits Success state`() = runTest {
        coEvery { rideRepository.getFareEstimate(pickup, drop) } returns Result.success(fakeEstimate)

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        val state = viewModel.uiState.value as FareEstimateUiState.Success
        assertEquals(85, state.estimate.estimatedFare)
        assertEquals(3.2, state.estimate.distanceKm, 0.001)
        assertEquals(12, state.estimate.durationMins)
        assertEquals(PaymentMethod.CASH, state.selectedPayment)
        assertEquals(false, state.isBooking)
    }

    @Test
    fun `load emits Error state on repository failure`() = runTest {
        coEvery { rideRepository.getFareEstimate(any(), any()) } returns
            Result.failure(Exception("No internet connection"))

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        val state = viewModel.uiState.value as FareEstimateUiState.Error
        assertEquals("No internet connection", state.message)
    }

    @Test
    fun `load emits generic message when exception has no message`() = runTest {
        coEvery { rideRepository.getFareEstimate(any(), any()) } returns
            Result.failure(Exception())

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        val state = viewModel.uiState.value as FareEstimateUiState.Error
        assertEquals("Could not get fare estimate.", state.message)
    }

    @Test
    fun `load passes correct pickup and drop to repository`() = runTest {
        coEvery { rideRepository.getFareEstimate(pickup, drop) } returns Result.success(fakeEstimate)

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        coVerify(exactly = 1) { rideRepository.getFareEstimate(pickup, drop) }
    }

    // ── Payment method selection ──────────────────────────────────

    @Test
    fun `onPaymentMethodSelected updates selectedPayment`() = runTest {
        coEvery { rideRepository.getFareEstimate(any(), any()) } returns Result.success(fakeEstimate)

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        viewModel.onPaymentMethodSelected(PaymentMethod.UPI)

        val state = viewModel.uiState.value as FareEstimateUiState.Success
        assertEquals(PaymentMethod.UPI, state.selectedPayment)
    }

    @Test
    fun `onPaymentMethodSelected is no-op when state is not Success`() = runTest {
        // State is Loading — nothing should change or crash
        viewModel.onPaymentMethodSelected(PaymentMethod.UPI)
        assertTrue(viewModel.uiState.value is FareEstimateUiState.Loading)
    }

    // ── Book ride ─────────────────────────────────────────────────

    @Test
    fun `onBookRide emits RideCreated event on success`() = runTest {
        val fakeRide = buildFakeRide("ride-id-001")

        coEvery { rideRepository.getFareEstimate(any(), any()) } returns Result.success(fakeEstimate)
        coEvery { rideRepository.createRide(pickup, drop, PaymentMethod.CASH) } returns Result.success(fakeRide)

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onBookRide()
            advanceUntilIdle()

            val event = awaitItem() as FareEstimateEvent.RideCreated
            assertEquals("ride-id-001", event.rideId)
        }
    }

    @Test
    fun `onBookRide passes selected payment method to createRide`() = runTest {
        val fakeRide = buildFakeRide("ride-id-002")

        coEvery { rideRepository.getFareEstimate(any(), any()) } returns Result.success(fakeEstimate)
        coEvery { rideRepository.createRide(pickup, drop, PaymentMethod.UPI) } returns Result.success(fakeRide)

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()
        viewModel.onPaymentMethodSelected(PaymentMethod.UPI)

        viewModel.onBookRide()
        advanceUntilIdle()

        coVerify(exactly = 1) { rideRepository.createRide(pickup, drop, PaymentMethod.UPI) }
    }

    @Test
    fun `onBookRide sets isBooking true then emits error on failure`() = runTest {
        coEvery { rideRepository.getFareEstimate(any(), any()) } returns Result.success(fakeEstimate)
        coEvery { rideRepository.createRide(any(), any(), any()) } returns
            Result.failure(Exception("Ride creation failed"))

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        viewModel.onBookRide()
        advanceUntilIdle()

        val state = viewModel.uiState.value as FareEstimateUiState.Error
        assertEquals("Ride creation failed", state.message)
    }

    @Test
    fun `onBookRide is no-op when state is not Success`() = runTest {
        // State is still Loading — createRide must never be called
        viewModel.onBookRide()
        advanceUntilIdle()
        coVerify(exactly = 0) { rideRepository.createRide(any(), any(), any()) }
    }

    // ── Retry ─────────────────────────────────────────────────────

    @Test
    fun `onRetry re-fetches fare estimate`() = runTest {
        coEvery { rideRepository.getFareEstimate(any(), any()) } returns
            Result.failure(Exception("error")) andThen Result.success(fakeEstimate)

        viewModel.load(pickup.lat, pickup.lng, pickup.address, drop.lat, drop.lng, drop.address)
        advanceUntilIdle()

        viewModel.onRetry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is FareEstimateUiState.Success)
        coVerify(exactly = 2) { rideRepository.getFareEstimate(any(), any()) }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun buildFakeRide(id: String) = Ride(
        id            = id,
        status        = RideStatus.REQUESTED,
        paymentMethod = PaymentMethod.CASH,
        paymentStatus = "PENDING",
        estimatedFare = 85,
        finalFare     = null,
        distanceKm    = null,
        durationMins  = null,
        pickupAddress = pickup.address,
        dropAddress   = drop.address,
        pickupLat     = pickup.lat,
        pickupLng     = pickup.lng,
        dropLat       = null,
        dropLng       = null,
        rideStartOtp  = null,
        customerRating = null,
        scheduledAt   = null,
        createdAt     = System.currentTimeMillis(),
        completedAt   = null,
        driver        = null,
        cancellationFee = null,
    )
}
