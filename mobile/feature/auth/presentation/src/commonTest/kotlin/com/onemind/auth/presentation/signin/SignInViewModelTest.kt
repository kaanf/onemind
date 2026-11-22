package com.onemind.auth.presentation.signin

import app.cash.turbine.test
import com.onemind.auth.presentation.FakeAuthRepository
import com.onemind.auth.presentation.MainDispatcherTest
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import com.onemind.core.presentation.generated.resources.Res
import com.onemind.core.presentation.generated.resources.error_no_internet
import com.onemind.core.presentation.util.UiText
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInViewModelTest : MainDispatcherTest() {
    private val repository = FakeAuthRepository()

    @Test
    fun sendIsEnabledOnlyForAPlausibleEmail() {
        val viewModel = SignInViewModel(repository)
        assertFalse(viewModel.state.value.canSend)

        viewModel.onAction(SignInAction.EmailChanged("owner@"))
        assertFalse(viewModel.state.value.canSend)

        viewModel.onAction(SignInAction.EmailChanged("owner@yourbar.cz"))
        assertTrue(viewModel.state.value.canSend)
    }

    @Test
    fun sendingTheCodeGoesToA2WithTheTrimmedEmail() =
        runTest(dispatcher) {
            val viewModel = SignInViewModel(repository)
            viewModel.onAction(SignInAction.EmailChanged(" owner@yourbar.cz "))

            viewModel.events.test {
                viewModel.onAction(SignInAction.SendCode)
                assertTrue(viewModel.state.value.isSending)
                assertEquals(SignInEvent.CodeSent("owner@yourbar.cz", 30), awaitItem())
            }
            assertEquals(listOf("owner@yourbar.cz"), repository.sent)
            assertFalse(viewModel.state.value.isSending)
        }

    @Test
    fun aRecentCodeContinuesToA2WithTheServersWait() =
        runTest(dispatcher) {
            repository.sendResult =
                Result.Failure(DataError.Api(429, "EMAIL_CODE_RESEND_TOO_SOON", params = mapOf("retryAfterSeconds" to "12")))
            val viewModel = SignInViewModel(repository)
            viewModel.onAction(SignInAction.EmailChanged("owner@yourbar.cz"))

            viewModel.events.test {
                viewModel.onAction(SignInAction.SendCode)
                assertEquals(SignInEvent.CodeSent("owner@yourbar.cz", 12), awaitItem())
            }
        }

    @Test
    fun aFailureShowsAMessageAndTypingClearsIt() =
        runTest(dispatcher) {
            repository.sendResult = Result.Failure(DataError.Network.NO_INTERNET)
            val viewModel = SignInViewModel(repository)
            viewModel.onAction(SignInAction.EmailChanged("owner@yourbar.cz"))

            viewModel.onAction(SignInAction.SendCode)
            advanceUntilIdle()
            assertEquals(UiText.Resource(Res.string.error_no_internet), viewModel.state.value.error)

            viewModel.onAction(SignInAction.EmailChanged("owner@yourbar.c"))
            assertNull(viewModel.state.value.error)
        }

    @Test
    fun anUpgradeRequiredShowsNoInlineMessage() =
        runTest(dispatcher) {
            repository.sendResult = Result.Failure(DataError.Api(426, "APP_UPGRADE_REQUIRED"))
            val viewModel = SignInViewModel(repository)
            viewModel.onAction(SignInAction.EmailChanged("owner@yourbar.cz"))

            viewModel.onAction(SignInAction.SendCode)
            advanceUntilIdle()

            assertNull(viewModel.state.value.error)
        }

    @Test
    fun stateEventsAndActionsNeverPrintTheEmail() {
        val email = "owner@yourbar.cz"
        listOf(SignInState(email = email), SignInEvent.CodeSent(email, 30), SignInAction.EmailChanged(email)).forEach {
            assertFalse(it.toString().contains(email), it.toString())
        }
    }

    @Test
    fun sendIsIgnoredWhileAlreadySending() =
        runTest(dispatcher) {
            val viewModel = SignInViewModel(repository)
            viewModel.onAction(SignInAction.EmailChanged("owner@yourbar.cz"))

            viewModel.onAction(SignInAction.SendCode)
            viewModel.onAction(SignInAction.SendCode)
            advanceUntilIdle()

            assertEquals(1, repository.sent.size)
        }
}
