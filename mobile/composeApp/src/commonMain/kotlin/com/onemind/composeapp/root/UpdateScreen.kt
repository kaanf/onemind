package com.onemind.composeapp.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.onemind.composeapp.generated.resources.Res
import com.onemind.composeapp.generated.resources.update_body
import com.onemind.composeapp.generated.resources.update_body_version
import com.onemind.composeapp.generated.resources.update_check_again
import com.onemind.composeapp.generated.resources.update_title
import com.onemind.core.designsystem.components.PrimaryButton
import com.onemind.core.designsystem.theme.OneMindTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Shown on `426 APP_UPGRADE_REQUIRED`. Not in the design (P7): a plain design-system screen. No store
 * button until the App Store / Play listings exist (phase 7); *Check again* hides it so the next
 * call asks the server again.
 */
@Composable
fun UpdateScreen(
    minimumVersion: String?,
    onCheckAgain: () -> Unit,
) {
    val colors = OneMindTheme.colors
    val spacing = OneMindTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.rowGap),
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(top = spacing.screenTop, bottom = spacing.screenBottom),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(text = stringResource(Res.string.update_title), style = OneMindTheme.typography.title, color = colors.textPrimary)
        Text(
            text =
                if (minimumVersion != null) {
                    stringResource(Res.string.update_body_version, minimumVersion)
                } else {
                    stringResource(Res.string.update_body)
                },
            style = OneMindTheme.typography.body,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        PrimaryButton(text = stringResource(Res.string.update_check_again), onClick = onCheckAgain)
    }
}

@Preview
@Composable
private fun UpdateScreenPreview() {
    OneMindTheme { UpdateScreen(minimumVersion = "1.5.0", onCheckAgain = {}) }
}
