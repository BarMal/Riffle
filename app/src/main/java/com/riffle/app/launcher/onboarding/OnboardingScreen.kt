package com.riffle.app.launcher.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.onboarding.OnboardingStep
import com.riffle.core.domain.launcher.onboarding.SourcePermission

internal const val ONBOARDING_TEST_TAG = "onboarding"
internal const val ONBOARDING_NEXT_TEST_TAG = "onboarding-next"
internal const val ONBOARDING_SKIP_TEST_TAG = "onboarding-skip"

internal fun onboardingPermissionTestTag(permission: SourcePermission): String = "onboarding-allow-${permission.name}"

/** What the Stream step knows about each permission: granted, or still to be asked. Unknown reads as not granted. */
internal fun interface PermissionGranted {
    fun isGranted(permission: SourcePermission): Boolean
}

/**
 * The four-step set-up: one short step per Place (Home, Dock, Library, Stream). The Stream step asks for
 * notification access, calendar and usage access, each after a one-line explanation and each skippable; nothing is
 * requested until the user taps Allow. Back on the first step, and "Skip set-up", leave without finishing; neither
 * changes anything the user placed.
 */
@Composable
internal fun OnboardingScreen(
    granted: PermissionGranted,
    onAllow: (SourcePermission) -> Unit,
    onFinished: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.HOME.name) }
    val step = OnboardingStep.valueOf(stepName)
    val goBack: () -> Unit = { step.previous()?.let { stepName = it.name } ?: onSkip() }
    androidx.activity.compose.BackHandler(onBack = goBack)
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(ONBOARDING_TEST_TAG)
                .semantics { paneTitle = OnboardingText.title(step) },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = RiffleSpacing.xl, vertical = RiffleSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            ) {
                Text(
                    text = OnboardingText.progress(step),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    text = OnboardingText.title(step),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = OnboardingText.body(step), style = MaterialTheme.typography.bodyLarge)
                if (step == OnboardingStep.STREAM) {
                    PermissionRows(granted = granted, onAllow = onAllow)
                }
            }
            Row(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(ONBOARDING_SKIP_TEST_TAG),
                    onClick = onSkip,
                ) { Text(OnboardingText.SKIP) }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s, alignment = Alignment.End),
                ) {
                    if (step.previous() != null) {
                        TextButton(onClick = goBack) { Text(OnboardingText.BACK) }
                    }
                    Button(
                        modifier = Modifier.testTag(ONBOARDING_NEXT_TEST_TAG),
                        onClick = { step.next()?.let { stepName = it.name } ?: onFinished() },
                    ) { Text(if (step.isLast) OnboardingText.DONE else OnboardingText.NEXT) }
                }
            }
        }
    }
}

@Composable
private fun PermissionRows(
    granted: PermissionGranted,
    onAllow: (SourcePermission) -> Unit,
) {
    var skipped by rememberSaveable { mutableStateOf(setOf<String>()) }
    Text(
        text = OnboardingText.STREAM_PERMISSIONS_HEADING,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    SourcePermission.entries.forEach { permission ->
        val isGranted = granted.isGranted(permission)
        val isSkipped = permission.name in skipped
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(RiffleSpacing.m),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            ) {
                Text(OnboardingText.permissionTitle(permission), style = MaterialTheme.typography.titleSmall)
                Text(
                    OnboardingText.permissionExplanation(permission),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    isGranted -> Text(OnboardingText.ALLOWED, style = MaterialTheme.typography.labelLarge)
                    isSkipped ->
                        Text(
                            OnboardingText.SKIPPED,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ->
                        Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                            FilledTonalButton(
                                modifier =
                                    Modifier
                                        .heightIn(min = RiffleSpacing.xxxl)
                                        .testTag(onboardingPermissionTestTag(permission)),
                                onClick = { onAllow(permission) },
                            ) { Text(OnboardingText.ALLOW) }
                            TextButton(
                                modifier = Modifier.heightIn(min = RiffleSpacing.xxxl),
                                onClick = { skipped = skipped + permission.name },
                            ) { Text(OnboardingText.NOT_NOW) }
                        }
                }
            }
        }
    }
}
