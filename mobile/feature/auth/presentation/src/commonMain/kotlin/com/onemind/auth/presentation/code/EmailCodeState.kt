package com.onemind.auth.presentation.code

import com.onemind.auth.domain.EmailCodeRules
import com.onemind.core.presentation.util.UiText

data class EmailCodeState(
    val email: String,
    val code: String = "",
    val isVerifying: Boolean = false,
    val isResending: Boolean = false,
    val resendSecondsLeft: Int = 0,
    val error: UiText? = null,
) {
    val canVerify: Boolean get() = !isVerifying && code.length == EmailCodeRules.CODE_LENGTH
    val canResend: Boolean get() = !isVerifying && !isResending && resendSecondsLeft == 0

    /** Neither the email nor the code may reach a log or crash report. */
    override fun toString(): String =
        "EmailCodeState(codeLength=${code.length}, isVerifying=$isVerifying, isResending=$isResending, " +
            "resendSecondsLeft=$resendSecondsLeft, error=$error)"
}

sealed interface EmailCodeAction {
    data class CodeChanged(
        val code: String,
    ) : EmailCodeAction {
        override fun toString(): String = "CodeChanged(length=${code.length})"
    }

    data object Verify : EmailCodeAction

    data object Resend : EmailCodeAction

    data object UseDifferentEmail : EmailCodeAction
}

sealed interface EmailCodeEvent {
    data object NavigateBack : EmailCodeEvent
}
