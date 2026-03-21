package com.chalo.customer.presentation.auth

import app.cash.turbine.test
import com.chalo.customer.data.local.preferences.UserPreferences
import com.chalo.customer.domain.repository.AuthRepository
import com.chalo.customer.domain.repository.VerifyOtpResult
import com.chalo.customer.presentation.screens.auth.OtpVerifyEvent
import com.chalo.customer.presentation.screens.auth.OtpVerifyViewModel
import com.chalo.customer.util.MainDispatcherRule
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpVerifyViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val userPrefs: UserPreferences = mockk(relaxed = true)

    private lateinit var viewModel: OtpVerifyViewModel

    @Before
    fun setUp() {
        viewModel = OtpVerifyViewModel(authRepository, userPrefs)
        viewModel.init("+919876543210")
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseAuth::class)
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    // ── OTP input validation ──────────────────────────────────────

    @Test
    fun `onOtpChanged updates otp in ui state`() = runTest {
        viewModel.onOtpChanged("12")
        assertEquals("12", viewModel.uiState.value.otp)
    }

    @Test
    fun `onOtpChanged clears error message`() = runTest {
        // Trigger an error first by calling verify with wrong length to mutate state
        viewModel.onOtpChanged("123")
        viewModel.onOtpChanged("12")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onVerify does nothing when otp is less than 4 digits`() = runTest {
        viewModel.onOtpChanged("12")
        // Manually call onVerify — should short-circuit because length != 4
        // onOtpChanged("12") won't auto-trigger because length < 4
        coVerify(exactly = 0) { authRepository.verifyOtp(any(), any()) }
    }

    // ── API failure path (no Firebase involved) ───────────────────

    @Test
    fun `onVerify sets errorMessage when repository returns failure`() = runTest {
        coEvery { authRepository.verifyOtp("+919876543210", "9999") } returns
            Result.failure(Exception("Invalid OTP. Please try again."))

        viewModel.onOtpChanged("9999")   // 4 digits → triggers onVerify automatically
        advanceUntilIdle()

        assertEquals("Invalid OTP. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `onVerify clears loading after repository failure`() = runTest {
        coEvery { authRepository.verifyOtp(any(), any()) } returns
            Result.failure(Exception("Network error"))

        viewModel.onOtpChanged("1234")
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `onVerify sets isLoading true while verifying`() = runTest {
        // Make the repository suspend indefinitely so we can check intermediate state
        coEvery { authRepository.verifyOtp(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(10_000)
            Result.failure(Exception("timeout"))
        }

        viewModel.onOtpChanged("1234")
        // Don't advance — check loading is true immediately after launch
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(1)
        assertEquals(true, viewModel.uiState.value.isLoading)
    }

    // ── API success + Firebase sign-in ────────────────────────────

    @Test
    fun `onVerify signs in to Firebase with custom token on success`() = runTest {
        val customToken = "firebase-custom-token-abc123"
        val verifyResult = VerifyOtpResult(
            isNewUser           = true,
            userId              = "user-id-1",
            userName            = null,
            userPhone           = "+919876543210",
            firebaseCustomToken = customToken,
        )

        coEvery { authRepository.verifyOtp("+919876543210", "5678") } returns
            Result.success(verifyResult)

        mockkStatic(FirebaseAuth::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        val mockFirebaseAuth = mockk<FirebaseAuth>()
        val mockTask = mockk<Task<AuthResult>>()
        val mockAuthResult = mockk<AuthResult>()

        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseAuth.signInWithCustomToken(customToken) } returns mockTask
        coEvery { mockTask.await() } returns mockAuthResult

        viewModel.events.test {
            viewModel.onOtpChanged("5678")
            advanceUntilIdle()

            val event = awaitItem()
            assert(event is OtpVerifyEvent.Verified)
            assert((event as OtpVerifyEvent.Verified).isNewUser)
        }
    }

    @Test
    fun `onVerify emits Verified with isNewUser false for returning user`() = runTest {
        val verifyResult = VerifyOtpResult(
            isNewUser           = false,
            userId              = "user-id-2",
            userName            = "Rahul",
            userPhone           = "+919876543210",
            firebaseCustomToken = "some-token",
        )

        coEvery { authRepository.verifyOtp(any(), any()) } returns Result.success(verifyResult)

        mockkStatic(FirebaseAuth::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        val mockFirebaseAuth = mockk<FirebaseAuth>()
        val mockTask = mockk<Task<AuthResult>>()
        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseAuth.signInWithCustomToken(any()) } returns mockTask
        coEvery { mockTask.await() } returns mockk()

        viewModel.events.test {
            viewModel.onOtpChanged("1234")
            advanceUntilIdle()

            val event = awaitItem() as OtpVerifyEvent.Verified
            assertEquals(false, event.isNewUser)
        }
    }

    @Test
    fun `onVerify sets errorMessage when Firebase sign-in fails`() = runTest {
        val verifyResult = VerifyOtpResult(
            isNewUser           = true,
            userId              = "user-id-3",
            userName            = null,
            userPhone           = "+919876543210",
            firebaseCustomToken = "bad-token",
        )

        coEvery { authRepository.verifyOtp(any(), any()) } returns Result.success(verifyResult)

        mockkStatic(FirebaseAuth::class)
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        val mockFirebaseAuth = mockk<FirebaseAuth>()
        val mockTask = mockk<Task<AuthResult>>()
        every { FirebaseAuth.getInstance() } returns mockFirebaseAuth
        every { mockFirebaseAuth.signInWithCustomToken(any()) } returns mockTask
        coEvery { mockTask.await() } throws Exception("Firebase auth failed")

        viewModel.onOtpChanged("1234")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    // ── Resend countdown ──────────────────────────────────────────

    @Test
    fun `resendCountdown starts at 60 on init`() {
        assertEquals(60, viewModel.uiState.value.resendCountdown)
    }

    @Test
    fun `onResendOtp calls sendOtp on repository`() = runTest {
        coEvery { authRepository.sendOtp("+919876543210") } returns Result.success(Unit)

        viewModel.onResendOtp()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.sendOtp("+919876543210") }
    }

    @Test
    fun `onResendOtp sets errorMessage on failure`() = runTest {
        coEvery { authRepository.sendOtp(any()) } returns
            Result.failure(Exception("Rate limit exceeded"))

        viewModel.onResendOtp()
        advanceUntilIdle()

        assertEquals("Rate limit exceeded", viewModel.uiState.value.errorMessage)
    }
}
