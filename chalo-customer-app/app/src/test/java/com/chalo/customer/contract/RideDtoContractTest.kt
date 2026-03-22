package com.chalo.customer.contract

import com.chalo.customer.data.remote.dto.ApiResponse
import com.chalo.customer.data.remote.dto.CancelRideResponseDto
import com.chalo.customer.data.remote.dto.FareEstimateDto
import com.chalo.customer.data.remote.dto.RideDto
import com.chalo.customer.data.remote.dto.RideHistoryItemDto
import com.chalo.customer.data.remote.dto.RideReceiptDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests: parse exact backend JSON responses through Gson and assert every
 * DTO field is correctly mapped.
 *
 * Backend source of truth:
 *   ride.validator.ts — fareEstimateSchema, createRideSchema, cancelRideSchema, rateRideSchema
 *   ride.service.ts   — response shapes for all ride endpoints
 *
 * Critical fields that have historically caused silent nulls if misnamed:
 *   - minimumFareApplied (camelCase — ensure @SerializedName matches)
 *   - rideStartOtp
 *   - cancellationWarning
 *   - surgeMultiplier
 */
class RideDtoContractTest {

    private lateinit var gson: Gson

    @Before
    fun setUp() {
        gson = Gson()
    }

    // ── POST /rides/fare-estimate ─────────────────────────────────

    @Test
    fun `fare estimate response maps all fields including minimumFare fields`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "estimatedFare": 85,
                "distanceKm": 3.2,
                "durationMins": 12,
                "baseFare": 50,
                "bookingFee": 10,
                "surgeMultiplier": 1.0,
                "minimumFareApplied": false,
                "minimumFare": 40
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<FareEstimateDto>>() {}.type
        val response: ApiResponse<FareEstimateDto> = gson.fromJson(json, type)

        assertTrue(response.success)
        val data = response.data!!
        assertEquals(85, data.estimatedFare)
        assertEquals(3.2, data.distanceKm, 0.001)
        assertEquals(12, data.durationMins)
        assertEquals(50, data.baseFare)
        assertEquals(10, data.bookingFee)
        assertEquals(1.0, data.surgeMultiplier, 0.001)
        assertFalse(data.minimumFareApplied)
        assertEquals(40, data.minimumFare)
    }

    @Test
    fun `fare estimate with minimumFareApplied=true and surge`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "estimatedFare": 40,
                "distanceKm": 0.3,
                "durationMins": 2,
                "baseFare": 25,
                "bookingFee": 5,
                "surgeMultiplier": 1.5,
                "minimumFareApplied": true,
                "minimumFare": 40
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<FareEstimateDto>>() {}.type
        val data = gson.fromJson<ApiResponse<FareEstimateDto>>(json, type).data!!

        assertTrue(data.minimumFareApplied)
        assertEquals(1.5, data.surgeMultiplier, 0.001)
        assertEquals(40, data.minimumFare)
    }

    // ── GET /rides/:rideId (RideDto) ──────────────────────────────

    @Test
    fun `ride details response maps all fields for REQUESTED ride`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "clride001",
                "status": "REQUESTED",
                "paymentMethod": "CASH",
                "paymentStatus": "PENDING",
                "estimatedFare": 85,
                "finalFare": null,
                "distanceKm": null,
                "durationMins": null,
                "pickupAddress": "Sadar Bazaar, Ludhiana",
                "dropAddress": "Civil Lines, Jalandhar",
                "pickupLat": 30.9010,
                "pickupLng": 75.8573,
                "dropLat": 31.3260,
                "dropLng": 75.5762,
                "rideStartOtp": null,
                "customerRating": null,
                "driverRating": null,
                "ratingSkippedAt": null,
                "scheduledAt": null,
                "createdAt": "2026-03-21T10:00:00.000Z",
                "completedAt": null,
                "driver": null,
                "cancellationFee": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<RideDto>>() {}.type
        val ride = gson.fromJson<ApiResponse<RideDto>>(json, type).data!!

        assertEquals("clride001", ride.id)
        assertEquals("REQUESTED", ride.status)
        assertEquals("CASH", ride.paymentMethod)
        assertEquals("PENDING", ride.paymentStatus)
        assertEquals(85, ride.estimatedFare)
        assertNull(ride.finalFare)
        assertNull(ride.rideStartOtp)
        assertNull(ride.driver)
        assertNull(ride.cancellationFee)
        assertEquals("2026-03-21T10:00:00.000Z", ride.createdAt)
    }

    @Test
    fun `ride details response maps DRIVER_ARRIVED ride with rideStartOtp`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "clride002",
                "status": "DRIVER_ARRIVED",
                "paymentMethod": "CASH",
                "paymentStatus": "PENDING",
                "estimatedFare": 90,
                "finalFare": null,
                "distanceKm": null,
                "durationMins": null,
                "pickupAddress": "Sadar Bazaar, Ludhiana",
                "dropAddress": "Civil Lines, Jalandhar",
                "pickupLat": 30.9010,
                "pickupLng": 75.8573,
                "dropLat": 31.3260,
                "dropLng": 75.5762,
                "rideStartOtp": "4823",
                "customerRating": null,
                "driverRating": null,
                "ratingSkippedAt": null,
                "scheduledAt": null,
                "createdAt": "2026-03-21T10:00:00.000Z",
                "completedAt": null,
                "driver": {
                  "id": "cldrv001",
                  "name": "Ravi Kumar",
                  "driverProfile": {
                    "vehicleNumber": "PB10AB1234",
                    "vehicleModel": "Honda Activa 6G",
                    "ratingAvg": 4.2,
                    "ratingCount": 38
                  }
                },
                "cancellationFee": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<RideDto>>() {}.type
        val ride = gson.fromJson<ApiResponse<RideDto>>(json, type).data!!

        assertEquals("4823", ride.rideStartOtp)
        assertNotNull(ride.driver)
        val driver = ride.driver!!
        assertEquals("cldrv001", driver.id)
        assertEquals("Ravi Kumar", driver.name)
        val profile = driver.driverProfile!!
        assertEquals("PB10AB1234", profile.vehicleNumber)
        assertEquals("Honda Activa 6G", profile.vehicleModel)
        assertEquals(4.2, profile.ratingAvg!!, 0.01)
        assertEquals(38, profile.ratingCount)
    }

    @Test
    fun `ride details response maps COMPLETED ride with finalFare and rating`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "clride003",
                "status": "COMPLETED",
                "paymentMethod": "UPI",
                "paymentStatus": "COMPLETED",
                "estimatedFare": 85,
                "finalFare": 88,
                "distanceKm": 3.4,
                "durationMins": 14,
                "pickupAddress": "Sadar Bazaar, Ludhiana",
                "dropAddress": "Civil Lines, Jalandhar",
                "pickupLat": 30.9010,
                "pickupLng": 75.8573,
                "dropLat": 31.3260,
                "dropLng": 75.5762,
                "rideStartOtp": null,
                "customerRating": 5,
                "driverRating": 4,
                "ratingSkippedAt": null,
                "scheduledAt": null,
                "createdAt": "2026-03-21T10:00:00.000Z",
                "completedAt": "2026-03-21T10:18:00.000Z",
                "driver": null,
                "cancellationFee": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<RideDto>>() {}.type
        val ride = gson.fromJson<ApiResponse<RideDto>>(json, type).data!!

        assertEquals(88, ride.finalFare)
        assertEquals(3.4, ride.distanceKm!!, 0.001)
        assertEquals(14, ride.durationMins)
        assertEquals(5, ride.customerRating)
        assertEquals("2026-03-21T10:18:00.000Z", ride.completedAt)
    }

    @Test
    fun `ride details response maps SCHEDULED ride with scheduledAt`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "clride004",
                "status": "SCHEDULED",
                "paymentMethod": "CASH",
                "paymentStatus": "PENDING",
                "estimatedFare": 90,
                "finalFare": null,
                "distanceKm": null,
                "durationMins": null,
                "pickupAddress": "Sadar Bazaar, Ludhiana",
                "dropAddress": "Civil Lines, Jalandhar",
                "pickupLat": 30.9010,
                "pickupLng": 75.8573,
                "dropLat": null,
                "dropLng": null,
                "rideStartOtp": null,
                "customerRating": null,
                "driverRating": null,
                "ratingSkippedAt": null,
                "scheduledAt": "2026-03-22T08:00:00.000Z",
                "createdAt": "2026-03-21T10:00:00.000Z",
                "completedAt": null,
                "driver": null,
                "cancellationFee": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<RideDto>>() {}.type
        val ride = gson.fromJson<ApiResponse<RideDto>>(json, type).data!!

        assertEquals("SCHEDULED", ride.status)
        assertEquals("2026-03-22T08:00:00.000Z", ride.scheduledAt)
    }

    // ── POST /rides/:rideId/cancel ────────────────────────────────

    @Test
    fun `cancel ride response maps fee and warning`() {
        val json = """
            {
              "success": true,
              "message": "Ride cancelled",
              "data": {
                "rideId": "clride001",
                "status": "CANCELLED",
                "cancellationFee": 40,
                "cancellationWarning": true
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<CancelRideResponseDto>>() {}.type
        val data = gson.fromJson<ApiResponse<CancelRideResponseDto>>(json, type).data!!

        assertEquals("clride001", data.rideId)
        assertEquals("CANCELLED", data.status)
        assertEquals(40, data.cancellationFee)
        assertTrue(data.cancellationWarning!!)
    }

    @Test
    fun `cancel ride response with zero fee and null warning`() {
        val json = """
            {
              "success": true,
              "message": "Ride cancelled",
              "data": {
                "rideId": "clride002",
                "status": "CANCELLED",
                "cancellationFee": 0,
                "cancellationWarning": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<CancelRideResponseDto>>() {}.type
        val data = gson.fromJson<ApiResponse<CancelRideResponseDto>>(json, type).data!!

        assertEquals(0, data.cancellationFee)
        assertNull(data.cancellationWarning)
    }

    // ── GET /rides/:rideId/receipt ────────────────────────────────

    @Test
    fun `receipt response maps all fare and payment fields`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "rideId": "clride003",
                "pickupAddress": "Sadar Bazaar, Ludhiana",
                "dropAddress": "Civil Lines, Jalandhar",
                "distanceKm": 3.4,
                "durationMins": 14,
                "baseFare": 55,
                "bookingFee": 10,
                "surgeMultiplier": 1.0,
                "finalFare": 88,
                "paymentMethod": "CASH",
                "paymentStatus": "PAID",
                "completedAt": "2026-03-21T10:18:00.000Z",
                "driver": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<RideReceiptDto>>() {}.type
        val receipt = gson.fromJson<ApiResponse<RideReceiptDto>>(json, type).data!!

        assertEquals("clride003", receipt.rideId)
        assertEquals(3.4, receipt.distanceKm, 0.001)
        assertEquals(14, receipt.durationMins)
        assertEquals(55, receipt.baseFare)
        assertEquals(10, receipt.bookingFee)
        assertEquals(1.0, receipt.surgeMultiplier, 0.001)
        assertEquals(88, receipt.finalFare)
        assertEquals("CASH", receipt.paymentMethod)
        assertEquals("PAID", receipt.paymentStatus)
        assertEquals("2026-03-21T10:18:00.000Z", receipt.completedAt)
        assertNull(receipt.driver)
    }

    // ── GET /rides/history ────────────────────────────────────────

    @Test
    fun `ride history item maps all fields`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": [
                {
                  "id": "clride005",
                  "pickupAddress": "Sadar Bazaar, Ludhiana",
                  "dropAddress": "Civil Lines, Jalandhar",
                  "finalFare": 88,
                  "status": "COMPLETED",
                  "paymentMethod": "CASH",
                  "distanceKm": 3.4,
                  "customerRating": 5,
                  "createdAt": "2026-03-21T10:00:00.000Z",
                  "completedAt": "2026-03-21T10:18:00.000Z",
                  "driver": {
                    "id": "cldrv001",
                    "name": "Ravi Kumar",
                    "driverProfile": {
                      "vehicleNumber": "PB10AB1234",
                      "vehicleModel": "Honda Activa 6G",
                      "ratingAvg": 4.2,
                      "ratingCount": 38
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<List<RideHistoryItemDto>>>() {}.type
        val response: ApiResponse<List<RideHistoryItemDto>> = gson.fromJson(json, type)
        val item = response.data!!.first()

        assertEquals("clride005", item.id)
        assertEquals(88, item.finalFare)
        assertEquals("COMPLETED", item.status)
        assertEquals(3.4, item.distanceKm!!, 0.001)
        assertEquals(5, item.customerRating)
        assertEquals("Ravi Kumar", item.driver?.name)
    }

    @Test
    fun `ride history item maps cancelled ride with null finalFare`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": [
                {
                  "id": "clride006",
                  "pickupAddress": "Sadar Bazaar, Ludhiana",
                  "dropAddress": "Civil Lines, Jalandhar",
                  "finalFare": null,
                  "status": "CANCELLED",
                  "paymentMethod": "CASH",
                  "distanceKm": null,
                  "customerRating": null,
                  "createdAt": "2026-03-21T11:00:00.000Z",
                  "completedAt": null,
                  "driver": null
                }
              ]
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<List<RideHistoryItemDto>>>() {}.type
        val item = gson.fromJson<ApiResponse<List<RideHistoryItemDto>>>(json, type).data!!.first()

        assertNull(item.finalFare)
        assertNull(item.distanceKm)
        assertNull(item.customerRating)
        assertNull(item.completedAt)
        assertNull(item.driver)
    }
}
