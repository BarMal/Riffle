package com.riffle.app.launcher.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep

/** Why "Build my home" is not available right now (the walkthrough can still be reviewed and left). */
internal enum class ApplyBlock {
    /** The workspaces are loading, or the home holds item edits while they do: wait. */
    LOADING,

    /** The home is read-only this process: the existing notice says what to do. */
    READ_ONLY,
}

/** What the stateless [WalkthroughScreen] draws. */
internal data class WalkthroughScreenModel(
    val ui: WalkthroughUi,
    /** The apps the pickers list (Favourite apps, notification apps, the dock), by label. */
    val apps: List<AppPick> = emptyList(),
    val applyBlock: ApplyBlock? = null,
    val reducedMotion: Boolean = false,
)

internal const val WALKTHROUGH_TEST_TAG = "walkthrough"
internal const val WALKTHROUGH_PRIMARY_TEST_TAG = "walkthrough-primary"
internal const val WALKTHROUGH_BACK_TEST_TAG = "walkthrough-back"

/** At this width and wider (an unfolded foldable, a tablet) the step list sits beside the step. */
private val TwoPaneMinWidth: Dp = 600.dp
private val RailWidth: Dp = 220.dp
private val ContentMaxWidth: Dp = 720.dp

/**
 * The first-time walkthrough, full screen over the home. Stateless: renders [model] and sends every interaction to
 * [onAction] (the session reduces it). Every step has Back (the system Back too) and "Skip set-up", which keeps the
 * home as it is. A step change cross-fades ([RiffleMotion.standard], a snap under reduced motion) and its title is a
 * polite live region, so TalkBack announces the new step. Content respects the safe drawing insets.
 */
@Composable
internal fun WalkthroughScreen(
    model: WalkthroughScreenModel,
    onAction: (WalkthroughAction) -> Unit,
    modifier: Modifier = Modifier,
    onCheckAgain: () -> Unit = {},
) {
    val ui = model.ui
    Surface(
        modifier = modifier.fillMaxSize().testTag(WALKTHROUGH_TEST_TAG),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            Row(modifier = Modifier.fillMaxSize()) {
                if (twoPane) {
                    StepRail(ui, Modifier.width(RailWidth).fillMaxHeight())
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = ContentMaxWidth)
                                .fillMaxSize()
                                .padding(horizontal = RiffleSpacing.l),
                    ) {
                        WalkthroughTopBar(ui, onAction)
                        StepBody(model, onAction, onCheckAgain, Modifier.weight(1f))
                        WalkthroughBottomBar(model, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkthroughTopBar(
    ui: WalkthroughUi,
    onAction: (WalkthroughAction) -> Unit,
) {
    val count = ui.steps.size
    val progress = WalkthroughText.progress(ui.position, count)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = progress,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onAction(WalkthroughAction.Cancel) },
                enabled = ui.state.pendingRequest == null,
            ) { Text(WalkthroughText.SKIP) }
        }
        LinearProgressIndicator(
            progress = { ui.position / count.coerceAtLeast(1).toFloat() },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = progress },
        )
        // The step's name, kept outside the cross-fade so a change is announced (polite) rather than lost.
        Text(
            text = WalkthroughText.stepName(ui.state.step),
            modifier =
                Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StepBody(
    model: WalkthroughScreenModel,
    onAction: (WalkthroughAction) -> Unit,
    onCheckAgain: () -> Unit,
    modifier: Modifier,
) {
    val ui = model.ui
    // The Options step walks through its goals; each goal is its own page of the cross-fade.
    val key = ui.state.step to (if (ui.state.step == WalkthroughStep.OPTIONS) ui.state.optionsIndex else 0)
    AnimatedContent(
        targetState = key,
        modifier = modifier.fillMaxWidth().padding(top = RiffleSpacing.m),
        transitionSpec = {
            fadeIn(RiffleMotion.standard(model.reducedMotion)) togetherWith
                fadeOut(RiffleMotion.standard(model.reducedMotion))
        },
        label = "walkthrough-step",
    ) { (step, _) ->
        when (step) {
            WalkthroughStep.WELCOME -> WelcomeStep()
            WalkthroughStep.GOALS -> GoalsStep(ui, onAction)
            WalkthroughStep.ARRANGE -> ArrangeStep(ui, onAction)
            WalkthroughStep.OPTIONS -> OptionsStep(model, onAction)
            WalkthroughStep.DOCK -> DockStep(model, onAction)
            WalkthroughStep.PERMISSIONS -> PermissionsStep(ui, onAction, onCheckAgain)
            WalkthroughStep.REVIEW -> ReviewStep(model)
            WalkthroughStep.DONE -> Spacer(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WalkthroughBottomBar(
    model: WalkthroughScreenModel,
    onAction: (WalkthroughAction) -> Unit,
) {
    val step = model.ui.state.step
    val pending = model.ui.state.pendingRequest != null
    val blocked = step == WalkthroughStep.REVIEW && model.applyBlock != null
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = RiffleSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step == WalkthroughStep.WELCOME) {
                TextButton(onClick = { onAction(WalkthroughAction.Cancel) }) { Text(WalkthroughText.SKIP_FOR_NOW) }
            } else {
                TextButton(
                    onClick = { onAction(WalkthroughAction.Back) },
                    enabled = !pending,
                    modifier = Modifier.testTag(WALKTHROUGH_BACK_TEST_TAG),
                ) { Text(WalkthroughText.BACK) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onAction(WalkthroughAction.Next) },
                enabled = model.ui.canAdvance && !blocked,
                modifier = Modifier.testTag(WALKTHROUGH_PRIMARY_TEST_TAG),
            ) { Text(WalkthroughText.primary(step)) }
        }
    }
}

/** The wide layout's list of steps: where you are, and what is done. Not interactive (Back and Next move). */
@Composable
private fun StepRail(
    ui: WalkthroughUi,
    modifier: Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Text(
                text = WalkthroughText.WELCOME_TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = RiffleSpacing.m),
            )
            ui.steps.filter { it != WalkthroughStep.DONE }.forEach { step ->
                val isCurrent = step == ui.state.step
                val done = step.ordinal < ui.state.step.ordinal
                Surface(
                    shape = RiffleShapes.medium,
                    color =
                        if (isCurrent) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                    modifier = Modifier.fillMaxWidth().semantics { selected = isCurrent },
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = RiffleSpacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
                    ) {
                        if (done) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(text = WalkthroughText.stepName(step), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
