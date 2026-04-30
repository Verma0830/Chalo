package com.chalo.customer.contract

import com.chalo.customer.data.remote.dto.ApiResponse
import com.chalo.customer.data.remote.dto.CustomerProfileDto
import com.chalo.customer.data.remote.dto.ProfileDto
import com.chalo.customer.data.remote.dto.SendOtpResponseDto
import com.chalo.customer.data.remote.dto.UserDto
import com.chalo.customer.data.remote.dto.VerifyOtpResponseDto
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
 * Contract tests: parse JSON strings that mirror exact backend responses through Gson
 * and assert every field maps to the correct DTO field.
 *
 * These tests are the safety net between the backend Zod schemas and the Android Gson DTOs.
 * A field rename or @SerializedName typo on either side will fail here before it silently
 * produces null at runtime.
 *
 * Backend source of truth:
 *   auth.validator.ts — sendOTPSchema, verifyOTPSchema, completeProfileSchema
 *   auth.routes.ts — response shapes from auth.service.ts
 */
class AuthDtoContractTest {

    private lateinit var gson: Gson

    @Before
    fun setUp() {
        gson = Gson()
    }

    // ── POST /auth/otp/send ───────────────────────────────────────

    @Test
    fun `sendOtp response parses message and expiresIn`() {
        // Exact shape returned by auth.service.ts sendOTP
        val json = """
            {
              "success": true,
              "message": "OTP sent successfully",
              "data": {
                "message": "OTP sent successfully",
                "expiresIn": 300
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<SendOtpResponseDto>>() {}.type
        val response: ApiResponse<SendOtpResponseDto> = gson.fromJson(json, type)

        assertTrue(response.success)
        assertEquals("OTP sent successfully", response.message)
        assertNotNull(response.data)
        assertEquals(300, response.data!!.expiresIn)
    }

    @Test
    fun `sendOtp error response parses success=false and message`() {
        val json = """
            {
              "success": false,
              "message": "Rate limit exceeded. Try again in 15 minutes.",
              "data": null
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<SendOtpResponseDto>>() {}.type
        val response: ApiResponse<SendOtpResponseDto> = gson.fromJson(json, type)

        assertFalse(response.success)
        assertEquals("Rate limit exceeded. Try again in 15 minutes.", response.message)
        assertNull(response.data)
    }

    // ── POST /auth/otp/verify ─────────────────────────────────────

    @Test
    fun `verifyOtp new user response parses all fields`() {
        // Shape returned by auth.service.ts verifyOTP for new user
        val json = """
            {
              "success": true,
              "message": "OTP verified",
              "data": {
                "isNewUser": true,
                "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.custom",
                "user": {
                  "id": "cluser123456",
                  "phone": "+919876543210",
                  "name": null,
                  "role": "CUSTOMER"
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<VerifyOtpResponseDto>>() {}.type
        val response: ApiResponse<VerifyOtpResponseDto> = gson.fromJson(json, type)

        assertTrue(response.success)
        val data = response.data!!
        assertTrue(data.isNewUser)
        assertEquals("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.custom", data.token)

        val user = data.user
        assertEquals("cluser123456", user.id)
        assertEquals("+919876543210", user.phone)
        assertNull(user.name)
        assertEquals("CUSTOMER", user.role)
    }

    @Test
    fun `verifyOtp returning user response parses name`() {
        val json = """
            {
              "success": true,
              "message": "OTP verified",
              "data": {
                "isNewUser": false,
                "token": "custom-token-returning",
                "user": {
                  "id": "cluser789",
                  "phone": "+919876543210",
                  "name": "Rahul Sharma",
                  "role": "CUSTOMER"
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<VerifyOtpResponseDto>>() {}.type
        val response: ApiResponse<VerifyOtpResponseDto> = gson.fromJson(json, type)

        val data = response.data!!
        assertFalse(data.isNewUser)
        assertEquals("Rahul Sharma", data.user.name)
    }

    @Test
    fun `verifyOtp wrong OTP error response`() {
        val json = """
            {
              "success": false,
              "message": "Invalid or expired OTP",
              "data": null
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<VerifyOtpResponseDto>>() {}.type
        val response: ApiResponse<VerifyOtpResponseDto> = gson.fromJson(json, type)

        assertFalse(response.success)
        assertEquals("Invalid or expired OTP", response.message)
    }

    // ── GET /auth/profile ─────────────────────────────────────────

    @Test
    fun `profile response parses all scalar fields`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "cluser111",
                "phone": "+919876543210",
                "name": "Priya Singh",
                "email": "priya@example.com",
                "role": "CUSTOMER",
                "languagePref": "en",
                "createdAt": "2026-03-21T10:00:00.000Z",
                "customerProfile": null
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<ProfileDto>>() {}.type
        val response: ApiResponse<ProfileDto> = gson.fromJson(json, type)

        assertTrue(response.success)
        val data = response.data!!
        assertEquals("cluser111", data.id)
        assertEquals("+919876543210", data.phone)
        assertEquals("Priya Singh", data.name)
        assertEquals("priya@example.com", data.email)
        assertEquals("CUSTOMER", data.role)
        assertEquals("en", data.languagePref)
        assertNull(data.customerProfile)
    }

    @Test
    fun `profile response parses nested customerProfile`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "cluser222",
                "phone": "+919876543210",
                "name": "Rahul",
                "email": null,
                "role": "CUSTOMER",
                "languagePref": "pa",
                "createdAt": "2026-03-21T10:00:00.000Z",
                "customerProfile": {
                  "emergencyContactName": "Mom",
                  "emergencyContactPhone": "+919999999999",
                  "savedHomeAddress": "Sadar Bazaar, Ludhiana",
                  "savedHomeLat": 30.9010,
                  "savedHomeLng": 75.8573,
                  "savedWorkAddress": "Cyber City, Gurugram",
                  "savedWorkLat": 28.4950,
                  "savedWorkLng": 77.0900,
                  "totalRides": 7,
                  "cancellationCount": 0
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<ProfileDto>>() {}.type
        val profile = gson.fromJson<ApiResponse<ProfileDto>>(json, type).data!!

        assertNull(profile.email)
        val cp: CustomerProfileDto = profile.customerProfile!!
        assertEquals("Mom", cp.emergencyContactName)
        assertEquals("+919999999999", cp.emergencyContactPhone)
        assertEquals("Sadar Bazaar, Ludhiana", cp.savedHomeAddress)
        assertEquals(30.9010, cp.savedHomeLat!!, 0.0001)
        assertEquals(75.8573, cp.savedHomeLng!!, 0.0001)
        assertEquals(7, cp.totalRides)
        assertEquals(0, cp.cancellationCount)
    }

    @Test
    fun `profile customerProfile with all null optional fields`() {
        val json = """
            {
              "success": true,
              "message": "ok",
              "data": {
                "id": "cluser333",
                "phone": "+919876543210",
                "name": "Test",
                "email": null,
                "role": "CUSTOMER",
                "languagePref": null,
                "createdAt": "2026-03-21T10:00:00.000Z",
                "customerProfile": {
                  "emergencyContactName": null,
                  "emergencyContactPhone": null,
                  "savedHomeAddress": null,
                  "savedHomeLat": null,
                  "savedHomeLng": null,
                  "savedWorkAddress": null,
                  "savedWorkLat": null,
                  "savedWorkLng": null,
                  "totalRides": null,
                  "cancellationCount": null
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<ProfileDto>>() {}.type
        val profile = gson.fromJson<ApiResponse<ProfileDto>>(json, type).data!!

        assertNull(profile.languagePref)
        val cp = profile.customerProfile!!
        assertNull(cp.emergencyContactName)
        assertNull(cp.savedHomeLat)
        assertNull(cp.totalRides)
    }

    // ── POST /auth/register-driver response ───────────────────────

    @Test
    fun `registerDriver response has same shape as verifyOtp with DRIVER role`() {
        val json = """
            {
              "success": true,
              "message": "Driver registered",
              "data": {
                "isNewUser": true,
                "token": "custom-driver-token",
                "user": {
                  "id": "cldriver001",
                  "phone": "+919999999999",
                  "name": "Ravi Kumar",
                  "role": "DRIVER"
                }
              }
            }
        """.trimIndent()

        val type = object : TypeToken<ApiResponse<VerifyOtpResponseDto>>() {}.type
        val response: ApiResponse<VerifyOtpResponseDto> = gson.fromJson(json, type)

        val data = response.data!!
        assertEquals("DRIVER", data.user.role)
        assertEquals("Ravi Kumar", data.user.name)
        assertTrue(data.isNewUser)
    }
}
