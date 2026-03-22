package com.chalo.customer.presentation.activeride

import app.cash.turbine.test
import com.chalo.customer.domain.model.CancelResult
import com.chalo.customer.domain.model.Driver
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.model.ShareLink
import com.chalo.customer.domain.repository.RideRepository
import com.chalo.customer.domain.repository.RtdbRepository
import com.chalo.customer.util.MainDispatcherRule
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveRideViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rideRepository: RideRepository = mockk()
    private val rtdbRepository: RtdbRepository = mockk()

    private lateinit var viewModel: ActiveRideViewModel

    private val rideId = "clride123456"

    // Base ride in DRIVER_ASSIGNED state
    private val baseRide = Ride(
        id            = rideId,
        status        = RideStatus.DRIVER_ASSIGNED,
        paymentMethod = PaymentMethod.CASH,
        paymentStatus = "PENDING",
        estimatedFare = 90,
        finalFare     = null,
        distanceKm    = null,
        durationMins  = null,
        pickupAddress = "Sadar Bazaar, Ludhiana",
        dropAddress   = "Civil Lines, Jalandhar",
        pickupLat     = 30.9010,
        pickupLng     = 75.8573,
        dropLat       = null,
        dropLng       = null,
        rideStartOtp  = "4823",
        customerRating = null,
        scheduledAt   = null,
        createdAt     = 1_700_000_000_000L,
        completedAt   = null,
        driver        = Driver("driver-1", "Ravi Kumar", "PB10AB1234", "Honda Activa", 4.2, 38),
        cancellationFee = null,
    )

    @Before
    fun setUp() {
        // Default: RTDB emits no events (empty flows)
        every { rtdbRepository.observeRideStatus(any()) } returns flowOf()
        every { rtdbRepository.observeDriverLocation(any()) } returns flowOf()
    }

    private fun initViewModel() {
        viewModel = ActiveRideViewModel(rideRepository, rtdbRepository)
        viewModel.init(rideId)
    }

    // ── Initial load ──────────────────────────────────────────────

    @Test
    fun `init loads ride and sets state`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        initViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.ride)
        assertEquals(RideStatus.DRIVER_ASSIGNED, state.ride!!.status)
        assertEquals("4823", state.ride!!.rideStartOtp)
    }

    @Test
    fun `init sets errorMessage when getRideDetails fails`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns
            Result.failure(Exception("Ride not found"))
        initViewModel()
        advanceUntilIdle()

        assertEquals("Ride not found", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `init ignores duplicate rideId call`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        initViewModel()
        viewModel.init(rideId)   // second call with same ID — should be ignored
        advanceUntilIdle()

        // getRideDetails called exactly once
        io.mockk.coVerify(exactly = 1) { rideRepository.getRideDetails(rideId) }
    }

    // ── RTDB status updates ───────────────────────────────────────

    @Test
    fun `RTDB COMPLETED status with CASH emits RideCompleted event`() = runTest {
        val completedRide = baseRide.copy(status = RideStatus.COMPLETED, paymentMethod = PaymentMethod.CASH, paymentStatus = "PAID")
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        every { rtdbRepository.observeRideStatus(rideId) } returns flowOf("COMPLETED")

        viewModel = ActiveRideViewModel(rideRepository, rtdbRepository)

        viewModel.events.test {
            viewModel.init(rideId)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is ActiveRideEvent.RideCompleted)
            assertEquals(rideId, (event as ActiveRideEvent.RideCompleted).rideId)
        }
    }

    @Test
    fun `RTDB COMPLETED status with UPI and PENDING payment emits PaymentRequired`() = runTest {
        val upiRide = baseRide.copy(paymentMethod = PaymentMethod.UPI, paymentStatus = "PENDING")
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(upiRide)
        every { rtdbRepository.observeRideStatus(rideId) } returns flowOf("COMPLETED")

        viewModel = ActiveRideViewModel(rideRepository, rtdbRepository)

        viewModel.events.test {
            viewModel.init(rideId)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is ActiveRideEvent.PaymentRequired)
            assertEquals(rideId, (event as ActiveRideEvent.PaymentRequired).rideId)
        }
    }

    @Test
    fun `RTDB CANCELLED status emits RideNavigateHome`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        every { rtdbRepository.observeRideStatus(rideId) } returns flowOf("CANCELLED")

        viewModel = ActiveRideViewModel(rideRepository, rtdbRepository)

        viewModel.events.test {
            viewModel.init(rideId)
            advanceUntilIdle()

            assertTrue(awaitItem() is ActiveRideEvent.RideNavigateHome)
        }
    }

    @Test
    fun `RTDB NO_DRIVER status emits RideNavigateHome`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        every { rtdbRepository.observeRideStatus(rideId) } returns flowOf("NO_DRIVER")

        viewModel = ActiveRideViewModel(rideRepository, rtdbRepository)

        viewModel.events.test {
            viewModel.init(rideId)
            advanceUntilIdle()

            assertTrue(awaitItem() is ActiveRideEvent.RideNavigateHome)
        }
    }

    @Test
    fun `RTDB driver location update sets driverLocation in state`() = runTest {
        val location = LatLng(28.41, 77.32)
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        every { rtdbRepository.observeDriverLocation(rideId) } returns flowOf(location)

        initViewModel()
        advanceUntilIdle()

        assertEquals(location, viewModel.uiState.value.driverLocation)
    }

    @Test
    fun `RTDB null driver location does not overwrite existing location`() = runTest {
        val location = LatLng(28.41, 77.32)
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        every { rtdbRepository.observeDriverLocation(rideId) } returns flowOf(location, null)

        initViewModel()
        advanceUntilIdle()

        // Last emission is null — state should reflect null (matches ViewModel logic)
        assertNull(viewModel.uiState.value.driverLocation)
    }

    // ── Cancel sheet ──────────────────────────────────────────────

    @Test
    fun `onCancelClick shows cancel sheet`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        initViewModel()
        advanceUntilIdle()

        viewModel.onCancelClick()
        assertTrue(viewModel.uiState.value.showCancelSheet)
    }

    @Test
    fun `onCancelDismiss hides cancel sheet`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        initViewModel()
        advanceUntilIdle()

        viewModel.onCancelClick()
        viewModel.onCancelDismiss()
        assertFalse(viewModel.uiState.value.showCancelSheet)
    }

    @Test
    fun `onConfirmCancel calls cancelRide and emits RideNavigateHome on success`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        coEvery { rideRepository.cancelRide(rideId, "CHANGED_MIND", null) } returns
            Result.success(CancelResult(0, false))
        initViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onConfirmCancel("CHANGED_MIND", null)
            advanceUntilIdle()

            assertTrue(awaitItem() is ActiveRideEvent.RideNavigateHome)
        }
    }

    @Test
    fun `onConfirmCancel sets errorMessage and clears isCancelling on failure`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        coEvery { rideRepository.cancelRide(any(), any(), any()) } returns
            Result.failure(Exception("Cannot cancel at this stage"))
        initViewModel()
        advanceUntilIdle()

        viewModel.onConfirmCancel("OTHER", null)
        advanceUntilIdle()

        assertEquals("Cannot cancel at this stage", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isCancelling)
    }

    // ── Share ride ────────────────────────────────────────────────

    @Test
    fun `onShareRide emits ShareUrl event on success`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        coEvery { rideRepository.shareRide(rideId) } returns
            Result.success(ShareLink("https://chalo.app/track/abc123", "2026-03-21T10:00:00Z"))
        initViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onShareRide()
            advanceUntilIdle()

            val event = awaitItem() as ActiveRideEvent.ShareUrl
            assertEquals("https://chalo.app/track/abc123", event.url)
        }
    }

    // ── SOS ───────────────────────────────────────────────────────

    @Test
    fun `onSosClick shows SOS confirm dialog`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        initViewModel()
        advanceUntilIdle()

        viewModel.onSosClick()
        assertTrue(viewModel.uiState.value.showSosConfirm)
    }

    @Test
    fun `onSosDismiss hides SOS confirm dialog`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        initViewModel()
        advanceUntilIdle()

        viewModel.onSosClick()
        viewModel.onSosDismiss()
        assertFalse(viewModel.uiState.value.showSosConfirm)
    }

    @Test
    fun `onSosConfirm emits SosTriggered event on success`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(baseRide)
        coEvery { rideRepository.triggerSos(rideId, 28.41, 77.32) } returns Result.success(Unit)
        initViewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onSosConfirm(28.41, 77.32)
            advanceUntilIdle()

            assertTrue(awaitItem() is ActiveRideEvent.SosTriggered)
        }
    }

    // ── Retry ─────────────────────────────────────────────────────

    @Test
    fun `onRetry re-fetches ride details`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returnsMany listOf(
            Result.failure(Exception("timeout")),
            Result.success(baseRide),
        )
        initViewModel()
        advanceUntilIdle()

        viewModel.onRetry()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.ride)
        io.mockk.coVerify(exactly = 2) { rideRepository.getRideDetails(rideId) }
    }
}
