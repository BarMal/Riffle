package com.riffle.app.launcher

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The dock's dynamic side: live apps (apps with something new), now playing while media is active, and the Stream
 * button, always last (ADR 0003, The Dock is the handle). It is also where a pull on the dock opens the Stream, so it
 * reports its bounds through [LocalDockDynamicBoundsSink].
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
    entries: List<DockDynamicEntry>,
    slotMetrics: DockSlotRenderMetrics,
    mainAxisDp: Int,
    runsHorizontally: Boolean,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    /** Receives the key of an entry whose intent is [DockDynamicEntryIntent.Delegate]. */
    onEntryDelegated: (String) -> Unit = {},
    reducedMotion: Boolean = false,
    // The edge against the section divider is an internal seam, not the dock's real edge -- see the
    // matching suppressEndFade on DockSlotStrip (#1174).
    suppressStartFade: Boolean = false,
) {
    if (entries.isEmpty() || mainAxisDp <= 0) {
        return
    }
    // The numbers the static side is drawn from, so an entry is the size of a pinned icon -- but never under the 48 dp
    // a touch target needs, which is also what the dock measured this section for (dockDynamicEntryExtentDp).
    val iconSizeDp = dockDynamicEntryExtentDp(slotMetrics.iconSizeDp)
    val spacingDp = slotMetrics.itemSpacingDp
    val scrollState = rememberScrollState()
    ScrollSelectedEntryIntoView(
        selectedIndex = entries.indexOfFirst(DockDynamicEntry::isSelected),
        entryPitchDp = iconSizeDp + spacingDp,
        reducedMotion = reducedMotion,
        scrollState = scrollState,
    )
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
                        DockDynamicEntryIntent.Delegate -> onEntryDelegated(entry.key)
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
    val boundsSink = LocalDockDynamicBoundsSink.current
    DisposableEffect(boundsSink) { onDispose { boundsSink(null) } }
    Box(
        modifier =
            Modifier
                .onGloballyPositioned { coordinates -> boundsSink(coordinates.boundsInRoot()) }
                .dockOverflowMask(
                    runsHorizontally = runsHorizontally,
                    fadeStart = overflowAffordance.showStart && !suppressStartFade,
                    fadeEnd = overflowAffordance.showEnd,
                ),
    ) {
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
    }
}

/**
 * Keeps whichever entry is marked selected in view, so the destination showing is never hidden past
 * the section's edge (#1212). Neutral: the dock does not know what "selected" means, only that the
 * supplier marked one entry as the one showing.
 */
@Composable
private fun ScrollSelectedEntryIntoView(
    selectedIndex: Int,
    entryPitchDp: Int,
    reducedMotion: Boolean,
    scrollState: ScrollState,
) {
    val selectedOffsetPx = with(LocalDensity.current) { (selectedIndex.coerceAtLeast(0) * entryPitchDp).dp.roundToPx() }
    LaunchedEffect(selectedIndex, selectedOffsetPx, reducedMotion) {
        if (selectedIndex < 0) return@LaunchedEffect
        if (reducedMotion) {
            scrollState.scrollTo(selectedOffsetPx)
        } else {
            scrollState.animateScrollTo(selectedOffsetPx)
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

    // The dynamic section's own entries are drawn from notifications (see this file's own doc), so
    // their icon can be a plain app icon, a messaging app's avatar-style icon, or a media
    // notification's play-button-style icon -- all three arrive as the same ImageBitmap through
    // appIconLoader/LauncherAppIcon, but before this they were masked to CircleShape here while
    // every other dock icon (HomeDock.kt's own shortcuts, folder/widget placeholders) used the
    // shared DOCK_ICON_MASK_CORNER_RADIUS_DP squircle instead -- one entry in the strip looked
    // like a different *kind* of control than its neighbours purely because of which section drew
    // it, not because of anything about the app itself. This tile now shares that same mask.
    val tileShape = RoundedCornerShape(DOCK_ICON_MASK_CORNER_RADIUS_DP.dp)
    val spotlightColor = rememberGlassSpotlightColor(identity = identity, appIconLoader = appIconLoader)
    Box(
        modifier =
            Modifier
                .requiredSize(iconSizeDp.dp)
                .glassSpotlightSource(key = "dynamic:${entry.key}", color = spotlightColor)
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
                            shape = tileShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.TopEnd,
    ) {
        DockDynamicTileIcon(entry, iconSizeDp, tileShape, appIconLoader)
        if (entry.symbol == DockDynamicSymbol.NowPlaying) NowPlayingMark(Modifier.align(Alignment.BottomEnd))
        NotificationCountBadge(count = entry.badgeCount)
    }
}

/** What fills a tile: the Stream button's glyph, the app's icon, or its initial when the launcher cannot resolve it. */
@Composable
private fun DockDynamicTileIcon(
    entry: DockDynamicEntry,
    iconSizeDp: Int,
    tileShape: Shape,
    appIconLoader: AppIconLoader,
) {
    val identity = entry.identity
    if (identity != null && entry.symbol != DockDynamicSymbol.Stream) {
        LauncherAppIcon(
            identity = identity,
            label = entry.label,
            iconLoader = appIconLoader,
            modifier = Modifier.requiredSize(iconSizeDp.dp),
            shape = tileShape,
        )
        return
    }
    Box(
        modifier =
            Modifier
                .requiredSize(iconSizeDp.dp)
                .clip(tileShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.symbol == DockDynamicSymbol.Stream) {
            Icon(
                imageVector = DockIcons.Stream,
                contentDescription = null,
                modifier = Modifier.requiredSize((iconSizeDp * STREAM_GLYPH_FRACTION).dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Text(
                text = entry.label.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** The small play mark in the corner of the playing app's icon. */
@Composable
private fun NowPlayingMark(modifier: Modifier) {
    Icon(
        imageVector = Icons.Filled.PlayArrow,
        contentDescription = null,
        modifier =
            modifier
                .requiredSize(NOW_PLAYING_MARK_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        tint = MaterialTheme.colorScheme.onPrimary,
    )
}

private const val STREAM_GLYPH_FRACTION = 0.58f
private const val NOW_PLAYING_MARK_DP = 18

/**
 * Where the dynamic section is, in root coordinates (null once it leaves composition), so the dock pull can tell which
 * side a touch started on. The dock host provides the sink; outside it nothing listens.
 */
internal val LocalDockDynamicBoundsSink = staticCompositionLocalOf<(Rect?) -> Unit> { {} }

/** An entry is a pinned icon's size, but never under a 48 dp touch target. */
internal fun dockDynamicEntryExtentDp(iconSizeDp: Int): Int = maxOf(iconSizeDp, DOCK_DYNAMIC_ENTRY_MIN_EXTENT_DP)

private const val DOCK_DYNAMIC_ENTRY_MIN_EXTENT_DP = 48

/** The ring marking the entry whose destination is currently showing (the Stream button while the Stream is up). */
private const val SELECTED_ENTRY_RING_DP = 2

internal fun dockDynamicSectionTileTestTag(label: String): String = "dock-dynamic-entry:$label"

internal const val DOCK_DYNAMIC_SECTION_TEST_TAG = "dock-dynamic-section"
