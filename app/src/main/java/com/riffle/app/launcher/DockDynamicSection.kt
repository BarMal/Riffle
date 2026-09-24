package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockModel

/**
 * The dock's dynamic side: the apps it is not already showing that have something waiting.
 *
 * Icons at dock size rather than the shelf's wide cards, because this runs *inside* the dock beside
 * the pinned items and has to read as the same strip. The shelf's card row is the same section with
 * room to say more; this is what it looks like with only a dock's thickness to work in.
 *
 * Whatever does not fit scrolls. The alternative -- shrinking the tiles to fit -- would make a
 * dynamic entry a different size from the pinned icon next to it, which is exactly the seam this
 * section exists to remove.
 */
@Composable
@Suppress("LongParameterList")
internal fun DockDynamicSection(
    dock: DockModel,
    entries: List<DockDynamicEntry>,
    slotMetrics: DockSlotRenderMetrics,
    mainAxisDp: Int,
    runsHorizontally: Boolean,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    onShowAllNotifications: () -> Unit = {},
    // The edge against the section divider is an internal seam, not the dock's real edge -- see the
    // matching suppressEndFade on DockSlotStrip (#1174).
    suppressStartFade: Boolean = false,
) {
    if (entries.isEmpty() || mainAxisDp <= 0) {
        return
    }
    // The same numbers the static side is drawn from, so an entry is the size of a pinned icon.
    val iconSizeDp = slotMetrics.iconSizeDp
    val spacingDp = slotMetrics.itemSpacingDp
    val scrollState = rememberScrollState()
    // No modifier parameter, against the usual convention: the section's extent is measured for it
    // by the dock and a caller-supplied one would only fight that.
    val runModifier =
        Modifier
            .testTag(DOCK_DYNAMIC_SECTION_TEST_TAG)
            .then(
                if (runsHorizontally) {
                    Modifier.width(mainAxisDp.dp).horizontalScroll(scrollState)
                } else {
                    Modifier.height(mainAxisDp.dp).verticalScroll(scrollState)
                },
            )
    val tiles: @Composable () -> Unit = {
        entries.forEach { entry ->
            DockDynamicSectionTile(
                entry = entry,
                iconSizeDp = iconSizeDp,
                appIconLoader = appIconLoader,
                onActivate = {
                    when (val intent = entry.intent) {
                        is DockDynamicEntryIntent.Dispatch -> onAction(intent.action)
                        DockDynamicEntryIntent.ShowAllNotifications -> onShowAllNotifications()
                        null -> Unit
                    }
                },
            )
        }
    }

    // Whatever does not fit scrolls (see this file's own doc above), so the section needs the same
    // "there is more" hint the static side already has -- previously missing here entirely, which
    // on a vertical (left/right edge) dock left no way to discover the dynamic side scrolls at all
    // (#1175).
    val overflowAffordance =
        DockOverflowAffordance(scrollOffsetPx = scrollState.value, maxScrollOffsetPx = scrollState.maxValue)
    val fadeColor = dockSurfaceColor(dock)
    Box {
        if (runsHorizontally) {
            Row(
                modifier = runModifier,
                horizontalArrangement = Arrangement.spacedBy(spacingDp.coerceAtLeast(0).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { tiles() }
        } else {
            Column(
                modifier = runModifier,
                verticalArrangement = Arrangement.spacedBy(spacingDp.coerceAtLeast(0).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { tiles() }
        }
        if (overflowAffordance.showStart && !suppressStartFade) {
            DockOverflowFade(runsHorizontally = runsHorizontally, atRunStart = true, color = fadeColor)
        }
        if (overflowAffordance.showEnd) {
            DockOverflowFade(runsHorizontally = runsHorizontally, atRunStart = false, color = fadeColor)
        }
    }
}

/**
 * One entry: the app's icon, badged with what is waiting, doing whatever a tap here means.
 *
 * The badge sits on the icon rather than beside it so the tile stays exactly one dock icon wide --
 * the run was measured on that assumption, and a tile that grew with its count would push the rest
 * of the section along every time a notification arrived. The selected ring is drawn behind the
 * icon for the same reason.
 */
@Composable
private fun DockDynamicSectionTile(
    entry: DockDynamicEntry,
    iconSizeDp: Int,
    appIconLoader: AppIconLoader,
    onActivate: () -> Unit,
) {
    val label = entry.label
    val identity = entry.identity
    val isSelected = entry.isSelected

    Box(
        modifier =
            Modifier
                .requiredSize(iconSizeDp.dp)
                .testTag(dockDynamicSectionTileTestTag(label))
                .semantics {
                    contentDescription = entry.contentDescription
                    selected = isSelected
                }
                .clickable(enabled = entry.intent != null, onClick = onActivate)
                // A ring over the icon's edge rather than anything behind or around it: the tile is
                // exactly one dock icon wide and the run was measured on that, so the mark for
                // "this is the one showing" cannot be allowed to take any room.
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = SELECTED_ENTRY_RING_DP.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (identity != null) {
            LauncherAppIcon(
                identity = identity,
                label = label,
                iconLoader = appIconLoader,
                modifier = Modifier.requiredSize(iconSizeDp.dp),
                shape = CircleShape,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .requiredSize(iconSizeDp.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.firstOrNull()?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        NotificationCountBadge(count = entry.badgeCount)
    }
}

/** The ring marking the entry whose stage is currently showing. */
private const val SELECTED_ENTRY_RING_DP = 2

internal fun dockDynamicSectionTileTestTag(label: String): String = "dock-dynamic-entry:$label"

internal const val DOCK_DYNAMIC_SECTION_TEST_TAG = "dock-dynamic-section"

/**
 * The notification entries this section will draw, empty for every shelf state that has none.
 *
 * A permission prompt is deliberately not one of them: it is a paragraph asking for access, which
 * has nowhere to go in a strip one icon deep. The shelf still shows it.
 */
internal fun DockNotificationShelfState.dynamicEntries(): List<DockDynamicEntry> {
    return dockNotificationCards().launchableDockDynamicEntries()
}

/**
 * The de-duplicated notification cards behind the dynamic section -- what an app pinned to the
 * static side is already excluded from. Cards mode builds stage-selecting entries from these rather
 * than the launch-opening ones [dynamicEntries] gives grid mode.
 */
internal fun DockNotificationShelfState.dockNotificationCards(): List<DockNotificationCardState> =
    (this as? DockNotificationShelfState.Content)?.cards.orEmpty()
