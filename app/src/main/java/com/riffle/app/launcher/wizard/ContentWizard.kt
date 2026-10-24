package com.riffle.app.launcher.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorNotice
import com.riffle.app.launcher.editor.EditorPreview
import com.riffle.app.launcher.editor.PreviewTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.wizard.AdvancedHandoff
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardExit
import com.riffle.core.domain.launcher.workspace.wizard.WizardFlow
import com.riffle.core.domain.launcher.workspace.wizard.WizardPreview
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import com.riffle.core.domain.launcher.workspace.wizard.WizardStep

/** At this width and wider (an unfolded foldable, a tablet) the preview sits beside the steps, as in the editor. */
private val TwoPaneMinWidth: Dp = 600.dp

/** Height of the Look step's preview above the looks in the single-pane layout. */
private val CompactPreviewHeight: Dp = 220.dp

/**
 * The content wizard, ready to host (docs/product/workspaces-wizard-and-onboarding.md): goal, what to show, how it
 * looks (with a live preview drawn by the real containers and expressions), where it goes, and saving it.
 *
 * - [context] is rebuilt by the host when access or the layout changes; every option and problem is read from it.
 * - [onFinished] receives the finished state: the host applies it (`WizardCommit`) and returns
 *   [WizardOutcome.Closed], or [WizardOutcome.Refused] to keep the wizard open on its last step with the reason.
 * - [onCancelled] follows Cancel, the close button, and Back on the first step; nothing was changed.
 * - [onAdvanced] opens the advanced lens builder with the draft so far.
 * - [onRequestSourceAccess] is called only from a Review access tap; map it to the existing explicit flows.
 *
 * The run survives rotation and process death (saved instance state). Back steps back; nothing is requested,
 * stored or started by showing it.
 */
@Suppress("LongParameterList") // The wizard's host contract: context, three exits and what the pickers need.
@Composable
internal fun ContentWizard(
    context: WizardContext,
    onFinished: (WizardState) -> WizardOutcome,
    onCancelled: () -> Unit,
    onAdvanced: (AdvancedHandoff) -> Unit,
    modifier: Modifier = Modifier,
    services: ContainerServices? = null,
    apps: List<AppPick> = emptyList(),
    onRequestSourceAccess: (SourceId) -> Unit = {},
    reducedMotion: Boolean = false,
) {
    val saver =
        remember(context.entry) {
            Saver<WizardUiState, Any>(
                save = { WizardStateCodec.encode(it.wizard) },
                restore = { WizardHolder.start(context, WizardStateCodec.decode(it)) },
            )
        }
    var ui by rememberSaveable(context.entry, stateSaver = saver) { mutableStateOf(WizardHolder.start(context)) }
    val dispatch: (WizardAction) -> Unit = { action ->
        val next = WizardHolder.reduce(ui, action, context)
        ui = next
        when (val exit = next.wizard.exit) {
            WizardExit.Cancelled -> onCancelled()
            is WizardExit.Advanced -> onAdvanced(exit.handoff)
            WizardExit.Finished -> {
                val outcome = onFinished(next.wizard)
                if (outcome is WizardOutcome.Refused) ui = WizardHolder.refused(next, outcome.message, context)
            }
            null -> Unit
        }
    }
    BackHandler(enabled = ui.wizard.exit == null) { dispatch(WizardAction.Back) }
    ContentWizardScreen(
        ui = ui,
        context = context,
        dispatch = dispatch,
        onDismissNotice = { ui = ui.copy(notice = null) },
        modifier = modifier,
        services = services,
        apps = apps,
        onRequestSourceAccess = onRequestSourceAccess,
        reducedMotion = reducedMotion,
    )
}

/**
 * The stateless wizard screen: renders [ui] and forwards every interaction to [dispatch]. Split from [ContentWizard]
 * so screenshot tests can draw any step without driving the flow by hand.
 */
@Suppress("LongParameterList")
@Composable
internal fun ContentWizardScreen(
    ui: WizardUiState,
    context: WizardContext,
    dispatch: (WizardAction) -> Unit,
    modifier: Modifier = Modifier,
    onDismissNotice: () -> Unit = {},
    services: ContainerServices? = null,
    apps: List<AppPick> = emptyList(),
    onRequestSourceAccess: (SourceId) -> Unit = {},
    reducedMotion: Boolean = false,
) {
    val state = ui.wizard
    Surface(modifier = modifier.fillMaxSize().testTag(WIZARD_TAG), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val preview = WizardFlow.preview(state)
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = RiffleSpacing.l)) {
                WizardTopBar(state, context, dispatch)
                ui.notice?.let {
                    EditorNotice(it, onDismiss = onDismissNotice, modifier = Modifier.testTag(WIZARD_NOTICE_TAG))
                }
                val body: @Composable (Modifier) -> Unit = { bodyModifier ->
                    WizardBody(
                        state = state,
                        context = context,
                        dispatch = dispatch,
                        apps = apps,
                        onRequestSourceAccess = onRequestSourceAccess,
                        reducedMotion = reducedMotion,
                        inlinePreview = if (!twoPane && state.step == WizardStep.LOOK) services else null,
                        modifier = bodyModifier,
                    )
                }
                if (twoPane) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        body(Modifier.weight(1f))
                        WizardPreviewPane(preview, services, Modifier.weight(1f).fillMaxSize())
                    }
                } else {
                    body(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun WizardTopBar(
    state: WizardState,
    context: WizardContext,
    dispatch: (WizardAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        IconButton(
            onClick = { dispatch(WizardAction.Cancel) },
            modifier = Modifier.testTag(WIZARD_CLOSE_TAG),
        ) { Icon(Icons.Filled.Close, contentDescription = WizardText.CANCEL) }
        Text(
            WizardText.title(context.entry),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(
            onClick = { dispatch(WizardAction.OpenAdvanced) },
            enabled = WizardFlow.handoff(state, context) != null,
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(WIZARD_ADVANCED_TAG),
        ) { Text(WizardText.ADVANCED) }
    }
}

/** The step: progress, heading, its controls (cross-fading, or snapping under reduced motion), problems, buttons. */
@Suppress("LongParameterList")
@Composable
private fun WizardBody(
    state: WizardState,
    context: WizardContext,
    dispatch: (WizardAction) -> Unit,
    apps: List<AppPick>,
    onRequestSourceAccess: (SourceId) -> Unit,
    reducedMotion: Boolean,
    inlinePreview: ContainerServices?,
    modifier: Modifier,
) {
    val (position, total) = WizardHolder.progress(state, context)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
        Column {
            Text(
                WizardText.progress(position, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                WizardText.heading(state.step),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        Crossfade(
            targetState = state.step,
            modifier = Modifier.weight(1f),
            animationSpec = RiffleMotion.standard(reducedMotion),
            label = "wizard-step",
        ) { step ->
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            ) {
                if (step != WizardStep.SOURCE) {
                    WizardHolder.neededAccess(state, context)?.let { (goal, needs) ->
                        NeedsAccessNote(goal, needs, onRequestSourceAccess)
                    }
                }
                if (inlinePreview != null && step == WizardStep.LOOK) {
                    WizardPreviewPane(
                        WizardFlow.preview(state),
                        inlinePreview,
                        Modifier.fillMaxWidth().height(CompactPreviewHeight),
                    )
                }
                when (step) {
                    WizardStep.SOURCE -> WizardSourceStep(state, context, dispatch, onRequestSourceAccess)
                    WizardStep.CONTENT -> WizardContentStep(state, context, apps, dispatch)
                    WizardStep.LOOK -> WizardLookStep(state, context, dispatch)
                    WizardStep.PLACE -> WizardPlaceStep(state, context, dispatch)
                    WizardStep.SAVE -> WizardSaveStep(state, context, dispatch)
                    WizardStep.DONE -> Unit
                }
            }
        }
        WizardProblems(state, context)
        WizardButtons(state, context, dispatch)
    }
}

/** The current step's problems, next to the buttons, announced politely when they change. */
@Composable
private fun WizardProblems(
    state: WizardState,
    context: WizardContext,
) {
    val text = WizardFlow.problems(state, context).map(WizardText::problem).distinct().joinToString(" ")
    if (text.isNotEmpty()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(WIZARD_PROBLEM_TAG).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun WizardButtons(
    state: WizardState,
    context: WizardContext,
    dispatch: (WizardAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = RiffleSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m, Alignment.End),
    ) {
        OutlinedButton(
            onClick = { dispatch(WizardAction.Back) },
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(WIZARD_BACK_TAG),
        ) { Text(WizardText.BACK) }
        Button(
            onClick = { dispatch(WizardAction.Next) },
            enabled = WizardFlow.canAdvance(state, context),
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl).testTag(WIZARD_NEXT_TAG),
        ) { Text(WizardText.primary(WizardHolder.isLast(state, context), WizardHolder.lensOnly(state))) }
    }
}

/**
 * The live preview: the binding drawn by the existing hosts over [services] (a page per group as a page-set draws
 * it), the picked apps for a placed goal, or a note before a goal is chosen. Transient: nothing it shows is stored.
 */
@Composable
internal fun WizardPreviewPane(
    preview: WizardPreview?,
    services: ContainerServices?,
    modifier: Modifier = Modifier,
) {
    val tagged = modifier.testTag(WIZARD_PREVIEW_TAG)
    when {
        services == null ->
            Text(
                WizardText.PREVIEW_NONE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = tagged,
            )
        preview == null -> EditorPreview(PreviewTarget.Empty(WizardText.PREVIEW_NONE), services, tagged)
        preview.placed -> PlacedAppsPreview(preview, tagged)
        preview.perGroup -> EditorPreview(PreviewTarget.PerGroup(preview.binding), services, tagged)
        else -> EditorPreview(PreviewTarget.Single(preview.binding), services, tagged)
    }
}

/** A placed goal's preview: the picked apps by name, as the home will show them (icons in the grid). */
@Composable
private fun PlacedAppsPreview(
    preview: WizardPreview,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = WizardText.PREVIEW },
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = RiffleElevation.level1,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(RiffleSpacing.l), contentAlignment = Alignment.TopStart) {
            Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
                Text(WizardText.PREVIEW_PLACED, style = MaterialTheme.typography.titleSmall)
                if (preview.apps.isEmpty()) {
                    Text(
                        WizardText.PREVIEW_PLACED_EMPTY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                preview.apps.forEach { Text(it.label, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
