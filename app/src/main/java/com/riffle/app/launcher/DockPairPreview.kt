package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.pair.PairIcon
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId

/** The id of a pinned item that may be paired (a plain app of the personal profile), else null. */
internal fun LauncherItem.pairableId(): LauncherItemId? =
    (this as? AppShortcutItem)
        ?.takeIf { it.appShortcutId == null && it.appIdentity.profile.type == AppProfileType.PERSONAL }
        ?.id

/** The pair a held drag would make: the target's app first, the dragged one second; null while nothing is held. */
internal fun pairPreviewFor(
    items: List<LauncherItem>,
    drag: DockDragState?,
): AppPairItem? {
    val targetId = drag?.pairTargetId ?: return null
    val target = items.firstOrNull { it.id == targetId } as? AppShortcutItem
    val dragged = items.firstOrNull { it.id == drag.itemId } as? AppShortcutItem
    return if (target == null || dragged == null) {
        null
    } else {
        AppPairItem(LauncherItemId(PREVIEW_PAIR_ID), target, dragged)
    }
}

internal const val DOCK_PAIR_PREVIEW_TEST_TAG = "dock-pair-preview"

private const val PREVIEW_PAIR_ID = "dock-pair:preview"

private const val WILL_PAIR_SCALE = 1.08f
private const val WILL_PAIR_MILLIS = 120

/**
 * The "will pair" state of the app a drag is held over: the fused icon of the pair a drop would make replaces the
 * app's own, a little larger. The scale is read in the draw phase.
 */
@Composable
internal fun DockPairPreview(
    pair: AppPairItem,
    iconSizeDp: Int,
    reducedMotion: Boolean,
    appIconLoader: AppIconLoader,
) {
    val scale = animateFloatAsState(WILL_PAIR_SCALE, tween(if (reducedMotion) 0 else WILL_PAIR_MILLIS), label = "pair")
    Box(
        modifier =
            Modifier
                .requiredSize(iconSizeDp.dp)
                .testTag(DOCK_PAIR_PREVIEW_TEST_TAG)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
    ) {
        PairIcon(
            pair = pair,
            iconLoader = appIconLoader,
            size = iconSizeDp.dp,
            shape = RoundedCornerShape(DOCK_ICON_MASK_CORNER_RADIUS_DP.dp),
            modifier = Modifier.requiredSize(iconSizeDp.dp),
        )
    }
}
