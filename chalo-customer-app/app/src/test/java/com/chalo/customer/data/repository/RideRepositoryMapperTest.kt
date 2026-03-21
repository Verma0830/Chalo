package com.chalo.customer.data.repository

import com.chalo.customer.data.local.dao.RideDao
import com.chalo.customer.data.remote.api.RideApiService
import com.chalo.customer.data.remote.dto.ApiResponse
import com.chalo.customer.data.remote.dto.CancelRideResponseDto
import com.chalo.customer.data.remote.dto.DriverProfileSummaryDto
import com.chalo.customer.data.remote.dto.DriverSummaryDto
import com.chalo.customer.data.remote.dto.FareEstimateDto
import com.chalo.customer.data.remote.dto.LocationDto
import com.chalo.customer.data.remote.dto.RideDto
import com.chalo.customer.data.remote.dto.RideReceiptDto
import com.chalo.customer.domain.model.Location
import com.chalo.customer.domain.model.PaymentMethod
import com.chalo.customer.domain.model.RideStatus
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the mapping logic inside RideRepositoryImpl:
 *   FareEstimateDto → FareEstimate
 *   RideDto → Ride
 *   RideReceiptDto → RideReceipt
 *   CancelRideResponseDto → CancelResult
 *
 * All tests use a mocked API service and RideDao — no network, no database.
 */
class RideRepositoryMapperTest {

    private val mockApi: RideApiService = mockk()
    private val mockDao: RideDao = mockk()
    private lateinit var repository: RideRepositoryImpl

    private val pickup = Location(28.4089, 77.3178, "Sector 14, Faridabad")
    private val drop   = Location(28.3852, 77.3126, "NIT Faridabad")

    @Before
    fun setUp() {
        repository = RideRepositoryImpl(mockApi, mockDao)
        // RideDao writes are fire-and-forget in tests
        coEvery { mockDao.insertRide(any()) } just runs
        coEvery { mockDao.insertRides(any()) } just runs
    }

    // ── FareEstimate mapper ───────────────────────────────────────

    @Test
    fun `getFareEstimate maps all fields`() = runTest {
        coEvery { mockApi.getFareEstimate(any()) } returns ApiResponse(
            success = true, message = "ok",
            data = FareEstimateDto(
                estimatedFare      = 85,
                distanceKm         = 3.2,
                durationMins       = 12,
                baseFare           = 50,
                bookingFee         = 10,
                surgeMultiplier    = 1.0,
                minimumFareApplied = false,
                minimumFare        = 40,
            )
        )

        val estimate = repository.getFareEstimate(pickup, drop).getOrThrow()
        assertEquals(85, estimate.estimatedFare)
        assertEquals(3.2, estimate.distanceKm, 0.001)
        assertEquals(12, estimate.durationMins)
        assertEquals(50, estimate.baseFare)
        assertEquals(10, estimate.bookingFee)
        assertEquals(1.0, estimate.surgeMultiplier, 0.001)
        assertFalse(estimate.minimumFareApplied)
        assertEquals(40, estimate.minimumFare)
    }

    @Test
    fun `getFareEstimate maps minimumFareApplied=true`() = runTest {
        coEvery { mockApi.getFareEstimate(any()) } returns ApiResponse(
            success = true, message = "ok",
            data = FareEstimateDto(
                estimatedFare = 40, distanceKm = 0.5, durationMins = 3,
                baseFare = 40, bookingFee = 0, surgeMultiplier = 1.0,
                minimumFareApplied = true, minimumFare = 40,
            )
        )

        val estimate = repository.getFareEstimate(pickup, drop).getOrThrow()
        assertTrue(estimate.minimumFareApplied)
        assertEquals(40, estimate.minimumFare)
    }

    @Test
    fun `getFareEstimate returns failure on API error`() = runTest {
        coEvery { mockApi.getFareEstimate(any()) } returns ApiResponse(
            success = false, message = "Pickup and drop too far apart", data = null
        )

        val result = repository.getFareEstimate(pickup, drop)
        assertTrue(result.isFailure)
        assertEquals("Pickup and drop too far apart", result.exceptionOrNull()?.message)
    }

    // ── RideDto → Ride mapper ─────────────────────────────────────

    @Test
    fun `getRideDetails maps status string to RideStatus enum`() = runTest {
        coEvery { mockApi.getRideDetails("ride-123") } returns ApiResponse(
            success = true, message = "ok", data = buildRideDto("ride-123", "DRIVER_ASSIGNED")
        )

        val ride = repository.getRideDetails("ride-123").getOrThrow()
        assertEquals(RideStatus.DRIVER_ASSIGNED, ride.status)
    }

    @Test
    fun `getRideDetails maps unknown status string to REQUESTED`() = runTest {
        coEvery { mockApi.getRideDetails("ride-123") } returns ApiResponse(
            success = true, message = "ok", data = buildRideDto("ride-123", "UNKNOWN_STATUS")
        )

        val ride = repository.getRideDetails("ride-123").getOrThrow()
        assertEquals(RideStatus.REQUESTED, ride.status)
    }

    @Test
    fun `getRideDetails maps paymentMethod string to PaymentMethod enum`() = runTest {
        coEvery { mockApi.getRideDetails("ride-upi") } returns ApiResponse(
            success = true, message = "ok",
            data = buildRideDto("ride-upi", "REQUESTED", paymentMethod = "UPI")
        )

        val ride = repository.getRideDetails("ride-upi").getOrThrow()
        assertEquals(PaymentMethod.UPI, ride.paymentMethod)
    }

    @Test
    fun `getRideDetails maps driver nested object`() = runTest {
        coEvery { mockApi.getRideDetails("ride-with-driver") } returns ApiResponse(
            success = true, message = "ok",
            data = buildRideDto("ride-with-driver", "DRIVER_ASSIGNED").copy(
                driver = DriverSummaryDto(
                    id = "drv-99",
                    name = "Ravi Kumar",
                    driverProfile = DriverProfileSummaryDto(
                        vehicleNumber = "HR01AB1234",
                        vehicleModel  = "Honda Activa 6G",
                        ratingAvg     = 4.2,
                        ratingCount   = 38,
                    )
                )
            )
        )

        val ride = repository.getRideDetails("ride-with-driver").getOrThrow()
        val driver = ride.driver!!
        assertEquals("drv-99", driver.id)
        assertEquals("Ravi Kumar", driver.name)
        assertEquals("HR01AB1234", driver.vehicleNumber)
        assertEquals("Honda Activa 6G", driver.vehicleModel)
        assertEquals(4.2, driver.ratingAvg!!, 0.01)
        assertEquals(38, driver.ratingCount)
    }

    @Test
    fun `getRideDetails maps null driver to null`() = runTest {
        coEvery { mockApi.getRideDetails("ride-no-driver") } returns ApiResponse(
            success = true, message = "ok",
            data = buildRideDto("ride-no-driver", "REQUESTED")   // driver = null by default
        )

        val ride = repository.getRideDetails("ride-no-driver").getOrThrow()
        assertNull(ride.driver)
    }

    @Test
    fun `getRideDetails maps rideStartOtp`() = runTest {
        coEvery { mockApi.getRideDetails("ride-otp") } returns ApiResponse(
            success = true, message = "ok",
            data = buildRideDto("ride-otp", "DRIVER_ARRIVED").copy(rideStartOtp = "7391")
        )

        val ride = repository.getRideDetails("ride-otp").getOrThrow()
        assertEquals("7391", ride.rideStartOtp)
    }

    @Test
    fun `getRideDetails parses ISO 8601 createdAt to epoch millis`() = runTest {
        coEvery { mockApi.getRideDetails("ride-ts") } returns ApiResponse(
            success = true, message = "ok",
            data = buildRideDto("ride-ts", "COMPLETED").copy(
                createdAt = "2026-03-21T10:00:00.000Z",
                completedAt = "2026-03-21T10:15:00.000Z",
            )
        )

        val ride = repository.getRideDetails("ride-ts").getOrThrow()
        assertTrue("createdAt should be > 0", ride.createdAt > 0)
        assertNotNull(ride.completedAt)
        assertTrue("completedAt should be after createdAt", ride.completedAt!! > ride.createdAt)
    }

    @Test
    fun `getRideDetails maps cancellationFee`() = runTest {
        coEvery { mockApi.getRideDetails("ride-cancelled") } returns ApiResponse(
            success = true, message = "ok",
            data = buildRideDto("ride-cancelled", "CANCELLED").copy(cancellationFee = 40)
        )

        val ride = repository.getRideDetails("ride-cancelled").getOrThrow()
        assertEquals(40, ride.cancellationFee)
    }

    // ── cancelRide → CancelResult mapper ─────────────────────────

    @Test
    fun `cancelRide maps cancellationFee and warning correctly`() = runTest {
        coEvery { mockApi.cancelRide("ride-x", any()) } returns ApiResponse(
            success = true, message = "ok",
            data = CancelRideResponseDto(
                rideId             = "ride-x",
                status             = "CANCELLED",
                cancellationFee    = 40,
                cancellationWarning = true,
            )
        )

        val result = repository.cancelRide("ride-x", "CHANGED_MIND", null).getOrThrow()
        assertEquals(40, result.cancellationFee)
        assertTrue(result.cancellationWarning)
    }

    @Test
    fun `cancelRide maps null cancellationWarning to false`() = runTest {
        coEvery { mockApi.cancelRide("ride-y", any()) } returns ApiResponse(
            success = true, message = "ok",
            data = CancelRideResponseDto(
                rideId = "ride-y", status = "CANCELLED",
                cancellationFee = 0, cancellationWarning = null,
            )
        )

        val result = repository.cancelRide("ride-y", "BOOKED_BY_MISTAKE", null).getOrThrow()
        assertFalse(result.cancellationWarning)
    }

    // ── RideReceiptDto → RideReceipt mapper ──────────────────────

    @Test
    fun `getRideReceipt maps all receipt fields`() = runTest {
        coEvery { mockApi.getRideReceipt("ride-done") } returns ApiResponse(
            success = true, message = "ok",
            data = RideReceiptDto(
                rideId          = "ride-done",
                pickupAddress   = "Sector 14, Faridabad",
                dropAddress     = "NIT Faridabad",
                distanceKm      = 3.2,
                durationMins    = 12,
                baseFare        = 50,
                bookingFee      = 10,
                surgeMultiplier = 1.0,
                finalFare       = 85,
                paymentMethod   = "CASH",
                paymentStatus   = "PAID",
                completedAt     = "2026-03-21T10:15:00.000Z",
                driver          = null,
            )
        )

        val receipt = repository.getRideReceipt("ride-done").getOrThrow()
        assertEquals("ride-done", receipt.rideId)
        assertEquals(85, receipt.finalFare)
        assertEquals("CASH", receipt.paymentMethod)
        assertEquals("PAID", receipt.paymentStatus)
        assertEquals(3.2, receipt.distanceKm, 0.001)
        assertTrue(receipt.completedAt > 0)
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun buildRideDto(
        id: String,
        status: String,
        paymentMethod: String = "CASH",
    ) = RideDto(
        id             = id,
        status         = status,
        paymentMethod  = paymentMethod,
        paymentStatus  = "PENDING",
        estimatedFare  = 85,
        finalFare      = null,
        distanceKm     = null,
        durationMins   = null,
        pickupAddress  = "Sector 14, Faridabad",
        dropAddress    = "NIT Faridabad",
        pickupLat      = 28.4089,
        pickupLng      = 77.3178,
        dropLat        = null,
        dropLng        = null,
        rideStartOtp   = null,
        customerRating = null,
        driverRating   = null,
        ratingSkippedAt = null,
        scheduledAt    = null,
        createdAt      = "2026-03-21T10:00:00.000Z",
        completedAt    = null,
        driver         = null,
        cancellationFee = null,
    )
}
