package com.onemind.auth.presentation.signin

import com.onemind.auth.domain.isPlausibleEmail
import com.onemind.core.presentation.util.UiText

data class SignInState(
    val email: String = "",
    val isSending: Boolean = false,
    val error: UiText? = null,
) {
    val canSend: Boolean get() = !isSending && isPlausibleEmail(email)

    /** The email is personal data: keep it out of logs and crash reports. */
    override fun toString(): String = "SignInState(isSending=$isSending, hasEmail=${email.isNotEmpty()}, error=$error)"
}

sealed interface SignInAction {
    data class EmailChanged(
        val email: String,
    ) : SignInAction {
        override fun toString(): String = "EmailChanged"
    }

    data object SendCode : SignInAction
}

sealed interface SignInEvent {
    /** Go to A2; resend stays disabled for [resendAfterSeconds]. */
    data class CodeSent(
        val email: String,
        val resendAfterSeconds: Int,
    ) : SignInEvent {
        override fun toString(): String = "CodeSent(resendAfterSeconds=$resendAfterSeconds)"
    }
}
