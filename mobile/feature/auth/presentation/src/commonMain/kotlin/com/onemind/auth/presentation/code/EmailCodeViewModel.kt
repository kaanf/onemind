package com.onemind.auth.presentation.code

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.auth.domain.AuthErrorCodes
import com.onemind.auth.domain.AuthRepository
import com.onemind.auth.domain.EmailCodeRules
import com.onemind.auth.presentation.util.retryAfterSeconds
import com.onemind.auth.presentation.util.toAuthMessage
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A2: the 6-digit code. A successful verify stores the session; the app gate then shows the signed-in
 * app, so there is no navigation event for it. Resend waits out a countdown (30 s, or the server's
 * `retryAfterSeconds`).
 */
class EmailCodeViewModel(
    email: String,
    resendAfterSeconds: Int,
    private val repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(EmailCodeState(email = email))
    val state: StateFlow<EmailCodeState> = _state.asStateFlow()

    private val _events = Channel<EmailCodeEvent>()
    val events = _events.receiveAsFlow()

    private var countdown: Job? = null

    init {
        startCountdown(resendAfterSeconds)
    }

    fun onAction(action: EmailCodeAction) {
        when (action) {
            is EmailCodeAction.CodeChanged -> {
                if (!_state.value.isVerifying) {
                    _state.update { it.copy(code = action.code.take(EmailCodeRules.CODE_LENGTH), error = null) }
                }
            }

            EmailCodeAction.Verify -> {
                verify()
            }

            EmailCodeAction.Resend -> {
                resend()
            }

            EmailCodeAction.UseDifferentEmail -> {
                // Not while verifying: the server may already have accepted (and used up) the code.
                if (!_state.value.isVerifying) viewModelScope.launch { _events.send(EmailCodeEvent.NavigateBack) }
            }
        }
    }

    private fun verify() {
        val current = _state.value
        if (!current.canVerify) return
        _state.update { it.copy(isVerifying = true, error = null) }
        viewModelScope.launch {
            // On success stay in "verifying" until the gate replaces this screen.
            val result = repository.verifyCode(current.email, current.code)
            if (result is Result.Failure) {
                val error = result.error
                // Only a wrong code clears the boxes (SCREENS.md A2); after a network error the user retries as is.
                val wrongCode = error is DataError.Api && error.code == AuthErrorCodes.EMAIL_CODE_INVALID
                _state.update { it.copy(isVerifying = false, code = if (wrongCode) "" else it.code, error = error.toAuthMessage()) }
            }
        }
    }

    private fun resend() {
        val current = _state.value
        if (!current.canResend) return
        _state.update { it.copy(isResending = true, error = null) }
        viewModelScope.launch {
            val result = repository.sendCode(current.email)
            _state.update { it.copy(isResending = false) }
            when (result) {
                is Result.Success -> {
                    _state.update { it.copy(code = "") }
                    startCountdown(EmailCodeRules.RESEND_AFTER_SECONDS)
                }

                is Result.Failure -> {
                    _state.update { it.copy(error = result.error.toAuthMessage()) }
                    result.error.retryAfterSeconds()?.let(::startCountdown)
                }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        _state.update { it.copy(resendSecondsLeft = seconds.coerceAtLeast(0)) }
        countdown =
            viewModelScope.launch {
                while (_state.value.resendSecondsLeft > 0) {
                    delay(1_000)
                    _state.update { it.copy(resendSecondsLeft = it.resendSecondsLeft - 1) }
                }
            }
    }
}
