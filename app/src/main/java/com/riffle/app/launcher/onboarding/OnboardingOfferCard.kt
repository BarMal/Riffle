package com.riffle.app.launcher.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing

internal const val ONBOARDING_OFFER_TEST_TAG = "onboarding-offer"

/**
 * The one-time "Set up your home" offer for existing users, on the home (like the Home-role card, never at the same
 * time). "Set up" opens the walkthrough, "Not now" dismisses it for good; either way the current home stays.
 */
@Composable
internal fun OnboardingOfferCard(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .testTag(ONBOARDING_OFFER_TEST_TAG)
                .semantics { paneTitle = WalkthroughText.OFFER_TITLE },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        ) {
            Text(
                text = WalkthroughText.OFFER_TITLE,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(text = WalkthroughText.OFFER_BODY, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s, alignment = Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(WalkthroughText.OFFER_DISMISS) }
                Button(onClick = onAccept) { Text(WalkthroughText.OFFER_ACCEPT) }
            }
        }
    }
}
