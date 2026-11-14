package com.riffle.app.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.stream.CardActionControls
import com.riffle.app.launcher.stream.OpenCardActions
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayout

internal const val DOCK_CARD_ACTIONS_TEST_TAG = "dock-card-actions"

/** The gap between the actions row and the Dock's own strip. */
private val ActionsRowGap = 6.dp

/**
 * The Dock's actions row (ADR 0003, Amendment 1, "Stream: browse, then open"): while a card is open the Dock gains a
 * row above its icons that holds that card's actions, so reading sits high and acting sits under the thumb. It is dense
 * with controls and a text field, so it is Thick glass in the Dock's own corner. It rises with the card's opening and
 * leaves with its closing (a short fade under reduced motion). The Dock grows upward to make room: the row is part of
 * the Dock's area, so what the Stream has left is what is above it. The row is inside the Dock's gesture, so a press
 * that starts on its empty space is still the Dock's press; the controls in it take their own taps and text.
 */
@Composable
internal fun DockActionsRow(
    actions: OpenCardActions?,
    cornerRadiusDp: Int,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // The content stays while the row leaves, so it does not blank before it has gone.
    var last by remember { mutableStateOf<OpenCardActions?>(null) }
    if (actions != null) last = actions
    val shown = actions ?: last
    AnimatedVisibility(
        visible = actions != null && actions.actions.isNotEmpty(),
        modifier = modifier,
        enter =
            if (reducedMotion) {
                fadeIn(tween(REDUCED_FADE_MILLIS))
            } else {
                expandVertically(RiffleMotion.smooth()) + fadeIn(RiffleMotion.smooth())
            },
        exit =
            if (reducedMotion) {
                fadeOut(tween(REDUCED_FADE_MILLIS))
            } else {
                shrinkVertically(RiffleMotion.smooth()) + fadeOut(RiffleMotion.smooth())
            },
    ) {
        if (shown != null) {
            val title = stringResource(R.string.stream_card_actions)
            Column(modifier = Modifier.semantics { isTraversalGroup = true }) {
                GlassSurface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(DOCK_CARD_ACTIONS_TEST_TAG)
                            .semantics { paneTitle = title },
                    weight = GlassWeight.Thick,
                    shape = RoundedCornerShape(cornerRadiusDp.dp),
                    tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    CardActionControls(
                        content = shown.content,
                        actions = shown.actions,
                        callbacks = shown.callbacks,
                        onToggleQueue = shown.onToggleQueue,
                        modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xxs),
                    )
                }
                Spacer(Modifier.height(ActionsRowGap))
            }
        }
    }
}

/** The actions row on the inner side of a Dock on [at]'s edge: nothing for a Dock on any other edge. */
@Composable
internal fun DockActionsRowAt(
    at: DockPosition,
    position: DockPosition,
    cardActions: OpenCardActions?,
    layout: HomeLayout,
    presentation: StandardHomePresentation,
    /** The row's height as it grows and goes (0 while none shows), so Home's room for the Dock can leave it out. */
    onRowHeightChanged: (Int) -> Unit = {},
) {
    if (position != at) return
    if (at == DockPosition.TOP) Spacer(modifier = Modifier.height(HOME_DOCK_TOP_SPACING_DP.dp))
    DockActionsRow(
        cardActions,
        layout.dock.cornerRadiusDp,
        presentation.reducedMotion,
        modifier = Modifier.onSizeChanged { size -> onRowHeightChanged(size.height) },
    )
}

private const val REDUCED_FADE_MILLIS = RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS
