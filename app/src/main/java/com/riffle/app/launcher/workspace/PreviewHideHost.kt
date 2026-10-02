package com.riffle.app.launcher.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsFeedback
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.app.launcher.expressions.ItemHider
import com.riffle.app.launcher.expressions.LocalItemHider
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

internal const val PREVIEW_HIDE_SNACKBAR_TEST_TAG = "workspace-preview-hide-snackbar"
internal const val PREVIEW_HIDE_EVERYWHERE_TEST_TAG = "workspace-preview-hide-everywhere"
internal const val PREVIEW_HIDE_UNDO_TEST_TAG = "workspace-preview-hide-undo"

/** Wording of the preview's Hide snackbar. The message itself comes from the controller (fixed phrases only). */
internal object PreviewHideText {
    const val HIDE_ON_ALL_LAYOUTS = "Hide on all layouts"
}

/** Gap between the dock bar and the snackbar. */
private val DockGap = 8.dp

/**
 * Gives every expression under [content] its "Hide" handler and shows the result in a snackbar: "Hidden on <layout>:
 * <fixed phrase>" with Undo and, after a hide on one layout, "Hide on all layouts". [controller] is null when the
 * runtime has no exclusion rules, in which case this draws [content] alone and no expression shows any Hide
 * affordance. The snackbar sits [dockBarHeight] above the bottom inset, clear of the dock. It is announced
 * politely (TalkBack reads it); nothing here stores or logs item content,
 * and the controller keeps the hidden item only until the snackbar ends.
 */
@Composable
internal fun PreviewHideHost(
    controller: ExclusionsSettingsController?,
    layout: HomeLayoutDeviceClass,
    dockBarHeight: Dp,
    content: @Composable () -> Unit,
) {
    if (controller == null) {
        content()
        return
    }
    val currentLayout by rememberUpdatedState(layout)
    val hider = remember(controller) { ItemHider { item, kind -> controller.hide(currentLayout, item, kind) } }
    val snackbar = remember { SnackbarHostState() }
    PreviewHideFeedbackEffect(controller, snackbar)
    CompositionLocalProvider(LocalItemHider provides hider) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            SnackbarHost(
                hostState = snackbar,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = dockBarHeight + DockGap),
            ) { data -> HideSnackbar(data.visuals, controller, data::performAction) }
        }
    }
}

/** What the snackbar shows for one announcement. */
private class HideSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    val canHideEverywhere: Boolean,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Long
}

@Composable
private fun HideSnackbar(
    visuals: SnackbarVisuals,
    controller: ExclusionsSettingsController,
    onUndo: () -> Unit,
) {
    val everywhere = (visuals as? HideSnackbarVisuals)?.canHideEverywhere == true
    Snackbar(
        modifier =
            Modifier
                .padding(horizontal = RiffleSpacing.l)
                .testTag(PREVIEW_HIDE_SNACKBAR_TEST_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
        action = {
            Row {
                if (everywhere) {
                    TextButton(
                        onClick = { controller.hideOnAllLayouts() },
                        modifier = Modifier.testTag(PREVIEW_HIDE_EVERYWHERE_TEST_TAG),
                    ) { Text(PreviewHideText.HIDE_ON_ALL_LAYOUTS) }
                }
                visuals.actionLabel?.let { label ->
                    TextButton(onClick = onUndo, modifier = Modifier.testTag(PREVIEW_HIDE_UNDO_TEST_TAG)) {
                        Text(label)
                    }
                }
            }
        },
    ) { Text(visuals.message) }
}

/** Shows each announcement; Undo calls the controller; leaving the preview drops whatever is pending. */
@Composable
private fun PreviewHideFeedbackEffect(
    controller: ExclusionsSettingsController,
    snackbar: SnackbarHostState,
) {
    val feedback by controller.feedback.collectAsState()
    LaunchedEffect(feedback?.id) {
        val current = feedback
        if (current != null) {
            val result = snackbar.showSnackbar(current.visuals())
            if (result == SnackbarResult.ActionPerformed) controller.undo()
            controller.feedbackShown(current.id)
        }
    }
    DisposableEffect(controller) { onDispose { controller.leave() } }
}

private fun ExclusionsFeedback.visuals(): SnackbarVisuals =
    HideSnackbarVisuals(
        message = message,
        actionLabel = ExclusionsSettingsText.UNDO.takeIf { canUndo },
        canHideEverywhere = canHideEverywhere,
    )
