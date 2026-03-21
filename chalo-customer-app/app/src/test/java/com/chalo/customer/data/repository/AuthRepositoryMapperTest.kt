package com.chalo.customer.data.repository

import com.chalo.customer.data.remote.api.AuthApiService
import com.chalo.customer.data.remote.dto.ApiResponse
import com.chalo.customer.data.remote.dto.CustomerProfileDto
import com.chalo.customer.data.remote.dto.ProfileDto
import com.chalo.customer.data.remote.dto.SendOtpResponseDto
import com.chalo.customer.data.remote.dto.UserDto
import com.chalo.customer.data.remote.dto.VerifyOtpResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the mapping logic inside AuthRepositoryImpl:
 *   ProfileDto → User domain model
 *   VerifyOtpResponseDto → VerifyOtpResult domain model
 *
 * Mapper functions are private, so we test them via the public repository methods
 * using a mocked API service — no network required.
 */
class AuthRepositoryMapperTest {

    private val mockApi: AuthApiService = mockk()
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        repository = AuthRepositoryImpl(mockApi)
    }

    // ── getProfile → ProfileDto mapper ────────────────────────────

    @Test
    fun `getProfile maps all scalar fields correctly`() = runTest {
        coEvery { mockApi.getProfile() } returns ApiResponse(
            success = true,
            message = "ok",
            data    = ProfileDto(
                id             = "user-123",
                phone          = "+919876543210",
                name           = "Rahul Sharma",
                email          = "rahul@example.com",
                role           = "CUSTOMER",
                languagePref   = "en",
                createdAt      = "2026-03-21T10:00:00.000Z",
                customerProfile = null,
            )
        )

        val result = repository.getProfile()

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("user-123", user.id)
        assertEquals("+919876543210", user.phone)
        assertEquals("Rahul Sharma", user.name)
        assertEquals("rahul@example.com", user.email)
        assertEquals("CUSTOMER", user.role)
        assertEquals("en", user.languagePref)
        assertNull(user.customerProfile)
    }

    @Test
    fun `getProfile maps customerProfile nested object`() = runTest {
        coEvery { mockApi.getProfile() } returns ApiResponse(
            success = true,
            message = "ok",
            data    = ProfileDto(
                id             = "user-456",
                phone          = "+919876543210",
                name           = "Priya",
                email          = null,
                role           = "CUSTOMER",
                languagePref   = "pa",
                createdAt      = "2026-03-21T10:00:00.000Z",
                customerProfile = CustomerProfileDto(
                    emergencyContactName  = "Mom",
                    emergencyContactPhone = "+919999999999",
                    savedHomeAddress      = "Sector 14, Faridabad",
                    savedHomeLat          = 28.4089,
                    savedHomeLng          = 77.3178,
                    savedWorkAddress      = "Cyber City, Gurugram",
                    savedWorkLat          = 28.4950,
                    savedWorkLng          = 77.0900,
                    totalRides            = 12,
                    cancellationCount     = 1,
                )
            )
        )

        val user = repository.getProfile().getOrThrow()
        val profile = user.customerProfile!!

        assertEquals("Mom", profile.emergencyContactName)
        assertEquals("+919999999999", profile.emergencyContactPhone)
        assertEquals("Sector 14, Faridabad", profile.savedHomeAddress)
        assertEquals(28.4089, profile.savedHomeLat!!, 0.0001)
        assertEquals(77.3178, profile.savedHomeLng!!, 0.0001)
        assertEquals(12, profile.totalRides)
        assertEquals(1, profile.cancellationCount)
    }

    @Test
    fun `getProfile maps null totalRides to 0`() = runTest {
        coEvery { mockApi.getProfile() } returns ApiResponse(
            success = true,
            message = "ok",
            data    = ProfileDto(
                id             = "user-789",
                phone          = "+919876543210",
                name           = "Test",
                email          = null,
                role           = "CUSTOMER",
                languagePref   = null,
                createdAt      = "2026-03-21T10:00:00.000Z",
                customerProfile = CustomerProfileDto(
                    emergencyContactName  = null,
                    emergencyContactPhone = null,
                    savedHomeAddress      = null,
                    savedHomeLat          = null,
                    savedHomeLng          = null,
                    savedWorkAddress      = null,
                    savedWorkLat          = null,
                    savedWorkLng          = null,
                    totalRides            = null,   // null from backend
                    cancellationCount     = null,
                )
            )
        )

        val user = repository.getProfile().getOrThrow()
        assertEquals(0, user.customerProfile!!.totalRides)
        assertEquals(0, user.customerProfile!!.cancellationCount)
    }

    @Test
    fun `getProfile returns failure when API returns success=false`() = runTest {
        coEvery { mockApi.getProfile() } returns ApiResponse(
            success = false,
            message = "Unauthorized",
            data    = null,
        )

        val result = repository.getProfile()
        assertTrue(result.isFailure)
        assertEquals("Unauthorized", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getProfile propagates network exception as failure`() = runTest {
        coEvery { mockApi.getProfile() } throws Exception("Connection timeout")

        val result = repository.getProfile()
        assertTrue(result.isFailure)
        assertEquals("Connection timeout", result.exceptionOrNull()?.message)
    }

    // ── verifyOtp → VerifyOtpResult mapper ───────────────────────

    @Test
    fun `verifyOtp maps isNewUser correctly`() = runTest {
        coEvery { mockApi.verifyOtp(any()) } returns ApiResponse(
            success = true,
            message = "ok",
            data    = VerifyOtpResponseDto(
                isNewUser = true,
                token     = "custom-token-abc",
                user      = UserDto("uid-1", "+919876543210", null, "CUSTOMER"),
            )
        )

        val result = repository.verifyOtp("+919876543210", "1234")
        assertTrue(result.isSuccess)
        val r = result.getOrThrow()
        assertTrue(r.isNewUser)
        assertEquals("uid-1", r.userId)
        assertNull(r.userName)
        assertEquals("+919876543210", r.userPhone)
        assertEquals("custom-token-abc", r.firebaseCustomToken)
    }

    @Test
    fun `verifyOtp maps returning user with name`() = runTest {
        coEvery { mockApi.verifyOtp(any()) } returns ApiResponse(
            success = true,
            message = "ok",
            data    = VerifyOtpResponseDto(
                isNewUser = false,
                token     = "custom-token-xyz",
                user      = UserDto("uid-2", "+919876543210", "Rahul", "CUSTOMER"),
            )
        )

        val r = repository.verifyOtp("+919876543210", "5678").getOrThrow()
        assertFalse(r.isNewUser)
        assertEquals("Rahul", r.userName)
    }

    @Test
    fun `sendOtp returns success when API responds successfully`() = runTest {
        coEvery { mockApi.sendOtp(any()) } returns ApiResponse(
            success = true,
            message = "OTP sent",
            data    = SendOtpResponseDto(message = "OTP sent", expiresIn = 300),
        )

        val result = repository.sendOtp("+919876543210")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendOtp returns failure when API responds with success=false`() = runTest {
        coEvery { mockApi.sendOtp(any()) } returns ApiResponse(
            success = false,
            message = "Rate limit exceeded",
            data    = null,
        )

        val result = repository.sendOtp("+919876543210")
        assertTrue(result.isFailure)
        assertEquals("Rate limit exceeded", result.exceptionOrNull()?.message)
    }
}
