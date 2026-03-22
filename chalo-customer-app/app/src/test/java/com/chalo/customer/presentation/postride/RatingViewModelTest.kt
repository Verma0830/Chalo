package com.chalo.customer.presentation.postride

import app.cash.turbine.test
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.model.Driver
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.domain.model.Ride
import com.chalo.customer.domain.model.RideStatus
import com.chalo.customer.domain.repository.RideRepository
import com.chalo.customer.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RatingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rideRepository: RideRepository = mockk()
    private val userPrefs: UserPreferences = mockk(relaxed = true)

    private lateinit var viewModel: RatingViewModel

    private val rideId = "clride789abc"

    private val completedRide = Ride(
        id             = rideId,
        status         = RideStatus.COMPLETED,
        paymentMethod  = PaymentMethod.CASH,
        paymentStatus  = "PAID",
        estimatedFare  = 90,
        finalFare      = 90,
        distanceKm     = 3.2,
        durationMins   = 12,
        pickupAddress  = "Sadar Bazaar, Ludhiana",
        dropAddress    = "Civil Lines, Jalandhar",
        pickupLat      = 30.9010,
        pickupLng      = 75.8573,
        dropLat        = 31.3260,
        dropLng        = 75.5762,
        rideStartOtp   = null,
        customerRating = null,
        scheduledAt    = null,
        createdAt      = 1_700_000_000_000L,
        completedAt    = 1_700_000_600_000L,
        driver         = Driver("drv-1", "Ravi Kumar", "PB10AB1234", "Honda Activa", 4.2, 38),
        cancellationFee = null,
    )

    @Before
    fun setUp() {
        // Default: pendingRatingShown emits false
        every { userPrefs.pendingRatingShown } returns flowOf(false)
        viewModel = RatingViewModel(rideRepository, userPrefs)
    }

    // ── init ──────────────────────────────────────────────────────

    @Test
    fun `init fetches ride and sets driverName`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)

        viewModel.init(rideId)
        advanceUntilIdle()

        assertEquals("Ravi Kumar", viewModel.uiState.value.driverName)
    }

    @Test
    fun `init sets driverName to null when ride has no driver`() = runTest {
        val rideWithoutDriver = completedRide.copy(driver = null)
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(rideWithoutDriver)

        viewModel.init(rideId)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.driverName)
    }

    // ── Star selection ────────────────────────────────────────────

    @Test
    fun `onRatingSelected updates rating in state`() {
        viewModel.onRatingSelected(4)
        assertEquals(4, viewModel.uiState.value.rating)
    }

    @Test
    fun `onRatingSelected updates rating from previous value`() {
        viewModel.onRatingSelected(3)
        viewModel.onRatingSelected(5)
        assertEquals(5, viewModel.uiState.value.rating)
    }

    // ── Comment input ─────────────────────────────────────────────

    @Test
    fun `onCommentChanged updates comment in state`() {
        viewModel.onCommentChanged("Great ride!")
        assertEquals("Great ride!", viewModel.uiState.value.comment)
    }

    // ── Submit rating ─────────────────────────────────────────────

    @Test
    fun `onSubmit does nothing when rating is 0`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.onSubmit()   // rating is still 0
        advanceUntilIdle()

        coVerify(exactly = 0) { rideRepository.rateRide(any(), any(), any()) }
    }

    @Test
    fun `onSubmit calls rateRide with correct rating and comment`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        coEvery { rideRepository.rateRide(rideId, 5, "Excellent!") } returns Result.success(Unit)
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.onRatingSelected(5)
        viewModel.onCommentChanged("Excellent!")
        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify(exactly = 1) { rideRepository.rateRide(rideId, 5, "Excellent!") }
    }

    @Test
    fun `onSubmit passes null comment when comment is blank`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        coEvery { rideRepository.rateRide(rideId, 3, null) } returns Result.success(Unit)
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.onRatingSelected(3)
        viewModel.onCommentChanged("   ")   // whitespace only → null
        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify(exactly = 1) { rideRepository.rateRide(rideId, 3, null) }
    }

    @Test
    fun `onSubmit emits Done event on success`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        coEvery { rideRepository.rateRide(any(), any(), any()) } returns Result.success(Unit)
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.onRatingSelected(5)

        viewModel.events.test {
            viewModel.onSubmit()
            advanceUntilIdle()

            val event = awaitItem()
            assertEquals(RatingEvent.Done, event)
        }
    }

    @Test
    fun `onSubmit clears pending rating in prefs on success`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        coEvery { rideRepository.rateRide(any(), any(), any()) } returns Result.success(Unit)
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.onRatingSelected(4)
        viewModel.onSubmit()
        advanceUntilIdle()

        coVerify(exactly = 1) { userPrefs.clearPendingRating() }
    }

    @Test
    fun `onSubmit sets errorMessage and clears isSubmitting on failure`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        coEvery { rideRepository.rateRide(any(), any(), any()) } returns
            Result.failure(Exception("Already rated"))
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.onRatingSelected(2)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("Already rated", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    // ── Rate later ────────────────────────────────────────────────

    @Test
    fun `onRateLater saves pending rating and emits Done`() = runTest {
        coEvery { rideRepository.getRideDetails(rideId) } returns Result.success(completedRide)
        viewModel.init(rideId)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onRateLater()
            advanceUntilIdle()

            coVerify(exactly = 1) { userPrefs.savePendingRating(rideId) }
            assertEquals(RatingEvent.Done, awaitItem())
        }
    }
}
