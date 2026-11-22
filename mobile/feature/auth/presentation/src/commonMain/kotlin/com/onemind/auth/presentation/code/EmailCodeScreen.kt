package com.onemind.auth.presentation.code

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.auth.presentation.generated.resources.Res
import com.onemind.auth.presentation.generated.resources.auth_back
import com.onemind.auth.presentation.generated.resources.auth_code_body
import com.onemind.auth.presentation.generated.resources.auth_code_different_email
import com.onemind.auth.presentation.generated.resources.auth_code_label
import com.onemind.auth.presentation.generated.resources.auth_code_resend
import com.onemind.auth.presentation.generated.resources.auth_code_resend_in
import com.onemind.auth.presentation.generated.resources.auth_code_resend_prompt
import com.onemind.auth.presentation.generated.resources.auth_code_title
import com.onemind.auth.presentation.generated.resources.auth_code_verify
import com.onemind.core.designsystem.components.InlineError
import com.onemind.core.designsystem.components.OtpInput
import com.onemind.core.designsystem.components.PrimaryButton
import com.onemind.core.designsystem.icons.OneMindIcons
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.ObserveAsEvents
import com.onemind.core.presentation.util.UiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EmailCodeRoot(
    email: String,
    resendAfterSeconds: Int,
    onBack: () -> Unit,
    viewModel: EmailCodeViewModel = koinViewModel { parametersOf(email, resendAfterSeconds) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            EmailCodeEvent.NavigateBack -> onBack()
        }
    }
    // While a code is checked, system back is swallowed too: leaving could burn a code the server
    // already accepted before the session is stored.
    // BackHandler is deprecated for NavigationEventHandler, whose API is still moving; switch with the next Compose bump.
    @Suppress("DEPRECATION")
    BackHandler(enabled = state.isVerifying) { }
    EmailCodeScreen(state = state, onAction = viewModel::onAction)
}

/** A2 per the design canvas (Cross-platform). Everything that leaves or edits is disabled while verifying. */
@Composable
fun EmailCodeScreen(
    state: EmailCodeState,
    onAction: (EmailCodeAction) -> Unit,
) {
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    val canLeave = !state.isVerifying
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .imePadding(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = spacing.topBarHeight).padding(horizontal = spacing.textGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onAction(EmailCodeAction.UseDifferentEmail) },
                enabled = canLeave,
                modifier = Modifier.size(spacing.minTouchTarget),
            ) {
                Icon(imageVector = OneMindIcons.Back, contentDescription = stringResource(Res.string.auth_back), tint = colors.textPrimary)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap),
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screenHorizontal)
                    .padding(top = spacing.sectionGap, bottom = spacing.screenBottom),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.textGap)) {
                Text(text = stringResource(Res.string.auth_code_title), style = OneMindTheme.typography.title, color = colors.textPrimary)
                Text(text = bodyText(state.email), style = OneMindTheme.typography.body, color = colors.textSecondary)
            }

            Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
                val label = stringResource(Res.string.auth_code_label)
                Text(text = label, style = OneMindTheme.typography.label, color = colors.textPrimary)
                OtpInput(
                    value = state.code,
                    onValueChange = { onAction(EmailCodeAction.CodeChanged(it)) },
                    contentDescription = label,
                    enabled = canLeave,
                    onDone = { onAction(EmailCodeAction.Verify) },
                )
                state.error?.let { InlineError(it.asString()) }
                ResendRow(state = state, onResend = { onAction(EmailCodeAction.Resend) })
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = stringResource(Res.string.auth_code_verify),
                    onClick = { onAction(EmailCodeAction.Verify) },
                    enabled = state.canVerify,
                )
                TextButton(
                    onClick = { onAction(EmailCodeAction.UseDifferentEmail) },
                    enabled = canLeave,
                    modifier = Modifier.heightIn(min = spacing.minTouchTarget),
                ) {
                    Text(
                        text = stringResource(Res.string.auth_code_different_email),
                        style = OneMindTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

/** "We sent a 6-digit code to **email**. …" with the address in bold, whatever the translation's word order. */
@Composable
private fun bodyText(email: String) =
    buildAnnotatedString {
        val template = stringResource(Res.string.auth_code_body, EMAIL_MARKER)
        val at = template.indexOf(EMAIL_MARKER)
        if (at < 0) {
            append(template)
            return@buildAnnotatedString
        }
        append(template.substring(0, at))
        withStyle(SpanStyle(color = OneMindTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(email) }
        append(template.substring(at + EMAIL_MARKER.length))
    }

@Composable
private fun ResendRow(
    state: EmailCodeState,
    onResend: () -> Unit,
) {
    val colors = OneMindTheme.colors
    val style = OneMindTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OneMindTheme.spacing.textGap / 2)) {
        Text(text = stringResource(Res.string.auth_code_resend_prompt), style = style, color = colors.textTertiary)
        if (state.resendSecondsLeft > 0) {
            Text(
                text = stringResource(Res.string.auth_code_resend_in, formatCountdown(state.resendSecondsLeft)),
                style = style,
                color = colors.textPlaceholder,
            )
        } else {
            TextButton(
                onClick = onResend,
                enabled = state.canResend,
                modifier = Modifier.heightIn(min = OneMindTheme.spacing.minTouchTarget),
            ) {
                Text(
                    text = stringResource(Res.string.auth_code_resend),
                    style = style.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
            }
        }
    }
}

/** `24` → `0:24`, `75` → `1:15`. */
internal fun formatCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}

private const val EMAIL_MARKER = "\u0000EMAIL\u0000"

@Preview
@Composable
private fun EmailCodeCountdownPreview() {
    OneMindTheme {
        EmailCodeScreen(
            state = EmailCodeState(email = "owner@yourbar.cz", code = "482", resendSecondsLeft = 24),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun EmailCodeVerifyingPreview() {
    OneMindTheme { EmailCodeScreen(state = EmailCodeState(email = "owner@yourbar.cz", code = "482913", isVerifying = true), onAction = {}) }
}

@Preview
@Composable
private fun EmailCodeErrorPreview() {
    OneMindTheme {
        EmailCodeScreen(
            state = EmailCodeState(email = "owner@yourbar.cz", error = UiText.Dynamic("That code isn't right. Attempts left: 3.")),
            onAction = {},
        )
    }
}
