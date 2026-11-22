package com.onemind.auth.presentation.code

import app.cash.turbine.test
import com.onemind.auth.presentation.FakeAuthRepository
import com.onemind.auth.presentation.MainDispatcherTest
import com.onemind.auth.presentation.generated.resources.Res
import com.onemind.auth.presentation.generated.resources.auth_error_code_attempts_exceeded
import com.onemind.auth.presentation.generated.resources.auth_error_code_expired
import com.onemind.auth.presentation.generated.resources.auth_error_code_invalid_attempts
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.presentation.util.UiText
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailCodeViewModelTest : MainDispatcherTest() {
    private val repository = FakeAuthRepository()
    private val email = "owner@yourbar.cz"

    private fun viewModel(resendAfter: Int = 30) = EmailCodeViewModel(email, resendAfter, repository)

    @Test
    fun resendCountsDownAndThenUnlocks() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            runCurrent()
            assertEquals(30, viewModel.state.value.resendSecondsLeft)
            assertFalse(viewModel.state.value.canResend)

            advanceTimeBy(6_001)
            assertEquals(24, viewModel.state.value.resendSecondsLeft)

            advanceTimeBy(24_000)
            assertEquals(0, viewModel.state.value.resendSecondsLeft)
            assertTrue(viewModel.state.value.canResend)
        }

    @Test
    fun verifyNeedsSixDigits() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(EmailCodeAction.CodeChanged("48291"))
            assertFalse(viewModel.state.value.canVerify)

            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()
            assertEquals(emptyList(), repository.verified)

            viewModel.onAction(EmailCodeAction.CodeChanged("4829135"))
            assertEquals("482913", viewModel.state.value.code)
            assertTrue(viewModel.state.value.canVerify)
        }

    @Test
    fun aCorrectCodeVerifiesAndKeepsTheSpinnerUntilTheGateSwitches() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(EmailCodeAction.CodeChanged("482913"))

            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()

            assertEquals(listOf(email to "482913"), repository.verified)
            assertTrue(viewModel.state.value.isVerifying)
        }

    @Test
    fun aWrongCodeClearsTheBoxesAndShowsAttemptsLeft() =
        runTest(dispatcher) {
            repository.verifyResult = Result.Failure(DataError.Api(422, "EMAIL_CODE_INVALID", params = mapOf("attemptsLeft" to "3")))
            val viewModel = viewModel()
            viewModel.onAction(EmailCodeAction.CodeChanged("000000"))

            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()

            val state = viewModel.state.value
            assertEquals("", state.code)
            assertFalse(state.isVerifying)
            assertEquals(UiText.Resource(Res.string.auth_error_code_invalid_attempts, listOf("3")), state.error)

            viewModel.onAction(EmailCodeAction.CodeChanged("1"))
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun expiredAndExhaustedCodesAskForANewOne() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            repository.verifyResult = Result.Failure(DataError.Api(422, "EMAIL_CODE_EXPIRED"))
            viewModel.onAction(EmailCodeAction.CodeChanged("482913"))
            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()
            assertEquals(UiText.Resource(Res.string.auth_error_code_expired), viewModel.state.value.error)

            repository.verifyResult = Result.Failure(DataError.Api(422, "EMAIL_CODE_ATTEMPTS_EXCEEDED"))
            viewModel.onAction(EmailCodeAction.CodeChanged("482913"))
            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()
            assertEquals(UiText.Resource(Res.string.auth_error_code_attempts_exceeded), viewModel.state.value.error)
        }

    @Test
    fun resendIsIgnoredDuringTheCountdown() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            runCurrent()

            viewModel.onAction(EmailCodeAction.Resend)
            runCurrent()

            assertEquals(emptyList(), repository.sent)
        }

    @Test
    fun resendAfterTheCountdownSendsAndRestartsIt() =
        runTest(dispatcher) {
            val viewModel = viewModel(resendAfter = 0)
            viewModel.onAction(EmailCodeAction.CodeChanged("123"))

            viewModel.onAction(EmailCodeAction.Resend)
            runCurrent()

            assertEquals(listOf(email), repository.sent)
            assertEquals(30, viewModel.state.value.resendSecondsLeft)
            assertEquals("", viewModel.state.value.code)
        }

    @Test
    fun aTooSoonResendWaitsTheServersRetryAfter() =
        runTest(dispatcher) {
            repository.sendResult =
                Result.Failure(DataError.Api(429, "EMAIL_CODE_RESEND_TOO_SOON", params = mapOf("retryAfterSeconds" to "9")))
            val viewModel = viewModel(resendAfter = 0)

            viewModel.onAction(EmailCodeAction.Resend)
            runCurrent()

            assertEquals(9, viewModel.state.value.resendSecondsLeft)
        }

    @Test
    fun useADifferentEmailNavigatesBack() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.events.test {
                viewModel.onAction(EmailCodeAction.UseDifferentEmail)
                assertEquals(EmailCodeEvent.NavigateBack, awaitItem())
            }
            advanceUntilIdle()
        }

    @Test
    fun whileVerifyingTheCodeCantChangeAndBackIsIgnored() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onAction(EmailCodeAction.CodeChanged("482913"))
            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()

            viewModel.events.test {
                viewModel.onAction(EmailCodeAction.UseDifferentEmail)
                viewModel.onAction(EmailCodeAction.CodeChanged("000000"))
                runCurrent()
                expectNoEvents()
            }
            assertEquals("482913", viewModel.state.value.code)
            assertFalse(viewModel.state.value.canResend)
        }

    @Test
    fun aNetworkErrorKeepsTheCodeForARetry() =
        runTest(dispatcher) {
            repository.verifyResult = Result.Failure(DataError.Network.NO_INTERNET)
            val viewModel = viewModel()
            viewModel.onAction(EmailCodeAction.CodeChanged("482913"))

            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()

            assertEquals("482913", viewModel.state.value.code)
            assertTrue(viewModel.state.value.canVerify)
        }

    @Test
    fun anExpiredCodeKeepsTheBoxes() =
        runTest(dispatcher) {
            repository.verifyResult = Result.Failure(DataError.Api(422, "EMAIL_CODE_EXPIRED"))
            val viewModel = viewModel()
            viewModel.onAction(EmailCodeAction.CodeChanged("482913"))

            viewModel.onAction(EmailCodeAction.Verify)
            runCurrent()

            assertEquals("482913", viewModel.state.value.code)
        }

    @Test
    fun stateAndActionsNeverPrintTheEmailOrCode() {
        val state = EmailCodeState(email = email, code = "482913")
        listOf(state.toString(), EmailCodeAction.CodeChanged("482913").toString()).forEach { text ->
            assertFalse(text.contains(email), text)
            assertFalse(text.contains("482913"), text)
        }
    }

    @Test
    fun countdownFormat() {
        assertEquals("0:24", formatCountdown(24))
        assertEquals("1:05", formatCountdown(65))
        assertEquals("0:00", formatCountdown(-3))
    }
}
