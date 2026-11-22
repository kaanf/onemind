package com.onemind.auth.presentation.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.auth.domain.AuthErrorCodes
import com.onemind.auth.domain.AuthRepository
import com.onemind.auth.domain.EmailCodeRules
import com.onemind.auth.presentation.util.retryAfterSeconds
import com.onemind.auth.presentation.util.toAuthMessage
import com.onemind.core.domain.util.DataError
import com.onemind.core.domain.util.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A1: email entry. Apple/Google come with their own roadmap item. */
class SignInViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state.asStateFlow()

    private val _events = Channel<SignInEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: SignInAction) {
        when (action) {
            is SignInAction.EmailChanged -> _state.update { it.copy(email = action.email, error = null) }
            SignInAction.SendCode -> sendCode()
        }
    }

    private fun sendCode() {
        val current = _state.value
        if (!current.canSend) return
        val email = current.email.trim()
        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val result = repository.sendCode(email)
            _state.update { it.copy(isSending = false) }
            when (result) {
                is Result.Success -> {
                    _events.send(SignInEvent.CodeSent(email, EmailCodeRules.RESEND_AFTER_SECONDS))
                }

                is Result.Failure -> {
                    val error = result.error
                    if (error is DataError.Api && error.code == AuthErrorCodes.EMAIL_CODE_RESEND_TOO_SOON) {
                        // A code went out moments ago (e.g. the user came back from A2): continue with it.
                        _events.send(SignInEvent.CodeSent(email, error.retryAfterSeconds() ?: EmailCodeRules.RESEND_AFTER_SECONDS))
                    } else {
                        _state.update { it.copy(error = error.toAuthMessage()) }
                    }
                }
            }
        }
    }
}
