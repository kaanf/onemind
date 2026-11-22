package com.onemind.auth.presentation.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.auth.presentation.generated.resources.Res
import com.onemind.auth.presentation.generated.resources.auth_brand
import com.onemind.auth.presentation.generated.resources.auth_email_label
import com.onemind.auth.presentation.generated.resources.auth_email_placeholder
import com.onemind.auth.presentation.generated.resources.auth_footer
import com.onemind.auth.presentation.generated.resources.auth_headline
import com.onemind.auth.presentation.generated.resources.auth_send_code
import com.onemind.auth.presentation.generated.resources.auth_subline
import com.onemind.core.designsystem.components.BrandMark
import com.onemind.core.designsystem.components.InlineError
import com.onemind.core.designsystem.components.OneMindTextField
import com.onemind.core.designsystem.components.PrimaryButton
import com.onemind.core.designsystem.theme.OneMindTheme
import com.onemind.core.presentation.util.ObserveAsEvents
import com.onemind.core.presentation.util.UiText
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInRoot(
    onCodeSent: (email: String, resendAfterSeconds: Int) -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SignInEvent.CodeSent -> onCodeSent(event.email, event.resendAfterSeconds)
        }
    }
    SignInScreen(state = state, onAction = viewModel::onAction)
}

/**
 * A1 per the design canvas (Cross-platform): brand, headline, email field and *Send me a code*.
 * *Continue with Apple / Google* and the *or with email* divider are left out until those providers
 * are wired (own roadmap item), so the screen never shows a button that does nothing.
 */
@Composable
fun SignInScreen(
    state: SignInState,
    onAction: (SignInAction) -> Unit,
) {
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal)
                .padding(top = spacing.screenTop, bottom = spacing.screenBottom),
    ) {
        BrandMark(name = stringResource(Res.string.auth_brand))

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.screenTop),
        ) {
            Text(text = stringResource(Res.string.auth_headline), style = OneMindTheme.typography.display, color = colors.textPrimary)
            Text(text = stringResource(Res.string.auth_subline), style = OneMindTheme.typography.bodyLarge, color = colors.textSecondary)
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(spacing.rowGap)) {
            OneMindTextField(
                value = state.email,
                onValueChange = { onAction(SignInAction.EmailChanged(it)) },
                label = stringResource(Res.string.auth_email_label),
                placeholder = stringResource(Res.string.auth_email_placeholder),
                enabled = !state.isSending,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Send,
                    ),
                keyboardActions = KeyboardActions(onSend = { onAction(SignInAction.SendCode) }),
            )
            state.error?.let { InlineError(it.asString()) }
            PrimaryButton(
                text = stringResource(Res.string.auth_send_code),
                onClick = { onAction(SignInAction.SendCode) },
                enabled = state.canSend,
            )
            Text(
                text = stringResource(Res.string.auth_footer),
                style = OneMindTheme.typography.caption,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SignInEmptyPreview() {
    OneMindTheme { SignInScreen(state = SignInState(), onAction = {}) }
}

@Preview
@Composable
private fun SignInFilledPreview() {
    OneMindTheme { SignInScreen(state = SignInState(email = "owner@yourbar.cz"), onAction = {}) }
}

@Preview
@Composable
private fun SignInErrorPreview() {
    OneMindTheme {
        SignInScreen(
            state = SignInState(email = "owner@yourbar.cz", error = UiText.Dynamic("No connection. Check your internet and try again.")),
            onAction = {},
        )
    }
}
