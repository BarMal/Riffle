package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.riffle.core.domain.launcher.dock.DockRunGeometry

/**
 * The dock's dynamic side: live apps (apps with something new), now playing while media is active and, in the
 * contextual mode, a tile for each Stream page that is not an app (ADR 0003, Amendment 3). It reports its bounds
 * through
 * [LocalDockDynamicBoundsSink], and where each entry is through [LocalDockDynamicEntryBoundsSink], so a press that
 * starts on an icon opens that icon's page.
 *
 * Compact 48 dp tiles (see [dockDynamicEntryExtentDp]), because this runs *inside* the dock beside the pinned items:
 * the side takes only the room its entries need (up to the dock's slot limit) and never the pinned icons'. The shelf's
 * card row is the same section with room to say more; this is what it looks like with only a dock's thickness.
 *
 * Whatever does not fit scrolls, fading at the edge that has more. The pinned run beside it scrolls the same way, at
 * full icon size: neither side is shrunk to make the other fit.
 */
@Composable
@Suppress("LongParameterList")
internal fun DockDynamicSection(
    entries: List<DockDynamicEntry>,
    slotMetrics: DockSlotRenderMetrics,
    mainAxisDp: Int,
    runsHorizontally: Boolean,
    appIconLoader: AppIconLoader,
    /** The dock's own interactions: where actions go, the haptics of a long-press, reduced motion. */
    interactions: DockInteractions,
    /** Receives the key of an entry whose intent is [DockDynamicEntryIntent.Delegate], or shown in the Stream. */
    onEntryDelegated: (String) -> Unit = {},
    // The edge against the section divider is an internal seam, not the dock's real edge -- see the
    // matching suppressEndFade on DockSlotStrip (#1174).
    suppressStartFade: Boolean = false,
    // The contextual mode's one scrolling row carries these entries with no scroll or mask of their own, the row
    // around them scrolls and keeps the ringed entry in view.
    inline: Boolean = false,
    // The index of the first contextual tile, which a divider precedes: the live entries come before it.
    dividerBeforeIndex: Int? = null,
) {
    if (entries.isEmpty() || mainAxisDp <= 0) {
        return
    }
    // The compact 48 dp tile the dock measured this section for (dockDynamicEntryExtentDp).
    val iconSizeDp = dockDynamicEntryExtentDp()
    val spacingDp = slotMetrics.itemSpacingDp
    val scrollState = rememberScrollState()
    if (!inline) {
        ScrollSelectedEntryIntoView(
            selectedIndex = entries.indexOfFirst(DockDynamicEntry::isSelected),
            entryPitchDp = iconSizeDp + spacingDp,
            reducedMotion = interactions.reducedMotion,
            scrollState = scrollState,
        )
    }
    // No modifier parameter, against the usual convention: the section's extent is measured for it
    // by the dock and a caller-supplied one would only fight that.
    val runModifier =
        Modifier
            .testTag(DOCK_DYNAMIC_SECTION_TEST_TAG)
            .then(
                when {
                    inline -> Modifier
                    runsHorizontally -> Modifier.width(mainAxisDp.dp).horizontalScroll(scrollState)
                    else -> Modifier.height(mainAxisDp.dp).verticalScroll(scrollState)
                },
            )
    val tiles: @Composable () -> Unit = {
        entries.forEachIndexed { index, entry ->
            if (index == dividerBeforeIndex) DockSectionDivider(runsHorizontally = runsHorizontally)
            DockDynamicSectionTile(
                entry = entry,
                iconSizeDp = iconSizeDp,
                appIconLoader = appIconLoader,
                onActivate = {
                    when (val intent = entry.intent) {
                        is DockDynamicEntryIntent.Dispatch -> interactions.onAction(intent.action)
                        DockDynamicEntryIntent.Delegate -> onEntryDelegated(entry.key)
                        null -> Unit
                    }
                },
                // "Show in Stream" is the entry's key handed back: a live app or now playing lands its page.
                onShowInStream = { onEntryDelegated(entry.key) },
                interactions = interactions,
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
                .then(
                    if (inline) {
                        Modifier
                    } else {
                        Modifier.dockOverflowMask(
                            runsHorizontally = runsHorizontally,
                            fadeStart = overflowAffordance.showStart && !suppressStartFade,
                            fadeEnd = overflowAffordance.showEnd,
                        )
                    },
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
@OptIn(ExperimentalFoundationApi::class)
private fun DockDynamicSectionTile(
    entry: DockDynamicEntry,
    iconSizeDp: Int,
    appIconLoader: AppIconLoader,
    onActivate: () -> Unit,
    onShowInStream: () -> Unit,
    interactions: DockInteractions,
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
    val entryBoundsSink = LocalDockDynamicEntryBoundsSink.current
    DisposableEffect(entry.key, entryBoundsSink) { onDispose { entryBoundsSink(entry.key, null) } }
    val isMenuExpanded = remember(entry.key) { mutableStateOf(false) }
    val hasMenu = entry.openInStreamMenuLabel != null || entry.appMenu.isNotEmpty()
    Box(
        modifier =
            Modifier
                .requiredSize(iconSizeDp.dp)
                // Where the tile lies, whole: a scrolling row clips what is off its edge, and `boundsInRoot` with it.
                .onGloballyPositioned { coordinates ->
                    entryBoundsSink(entry.key, Rect(coordinates.positionInRoot(), coordinates.size.toSize()))
                }
                // Hold to choose: the tile the finger has selected is larger and ringed.
                .dockChoiceMark(entry.key, tileShape, interactions.reducedMotion)
                .glassSpotlightSource(key = "dynamic:${entry.key}", color = spotlightColor)
                .testTag(dockDynamicSectionTileTestTag(label))
                .dynamicEntryAccess(entry, isSelected, onShowInStream)
                .combinedClickable(
                    enabled = entry.intent != null,
                    onClickLabel = entry.openLabel,
                    onClick = onActivate,
                    onLongClick =
                        if (hasMenu) {
                            {
                                interactions.haptics.longPress()
                                isMenuExpanded.value = true
                            }
                        } else {
                            null
                        },
                    onLongClickLabel = "Show $label actions".takeIf { hasMenu },
                )
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
        if (entry.symbol is DockDynamicSymbol.NowPlaying) NowPlayingMark(Modifier.align(Alignment.BottomEnd))
        NotificationCountBadge(count = entry.badgeCount)
        if (hasMenu) {
            DynamicEntryMenu(
                entry = entry,
                expanded = isMenuExpanded.value,
                onDismiss = { isMenuExpanded.value = false },
                onShowInStream = onShowInStream,
                onAction = interactions.onAction,
            )
        }
    }
}

/** What fills a tile: a contextual tile's glyph, the app's icon, or its initial when the launcher cannot resolve it. */
@Composable
private fun DockDynamicTileIcon(
    entry: DockDynamicEntry,
    iconSizeDp: Int,
    tileShape: Shape,
    appIconLoader: AppIconLoader,
) {
    val identity = entry.identity
    if (identity != null && entry.symbol !is DockDynamicSymbol.Glyph) {
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
        val glyph = (entry.symbol as? DockDynamicSymbol.Glyph)?.glyph
        if (glyph != null) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                modifier = Modifier.requiredSize((iconSizeDp * TILE_GLYPH_FRACTION).dp),
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

private const val TILE_GLYPH_FRACTION = 0.58f
private const val NOW_PLAYING_MARK_DP = 18

/**
 * Where the dynamic section is, in root coordinates (null once it leaves composition), so an expanded posture knows
 * which
 * end of the dock it is at. The dock host provides the sink; outside it nothing listens.
 */
internal val LocalDockDynamicBoundsSink = staticCompositionLocalOf<(Rect?) -> Unit> { {} }

/**
 * Where each dynamic entry is, in root coordinates by key (null once it leaves composition), so a press that starts on
 * an app's icon can land the Stream on that app. The dock host provides the sink; outside it nothing listens.
 */
internal val LocalDockDynamicEntryBoundsSink = staticCompositionLocalOf<(String, Rect?) -> Unit> { { _, _ -> } }

/**
 * A dynamic entry is a compact tile: exactly the 48 dp touch target, whatever size the pinned icons are. The pins keep
 * their own (Home-sized) icons and the dynamic side takes only this much room per entry, so a large pinned icon never
 * makes the side beside it grow, and an entry is never under a touch target.
 */
internal fun dockDynamicEntryExtentDp(): Int = DOCK_DYNAMIC_ENTRY_EXTENT_DP

private const val DOCK_DYNAMIC_ENTRY_EXTENT_DP = DockRunGeometry.CONTEXTUAL_ITEM_DP

/** The ring marking the entry whose page is open (contextual mode). */
private const val SELECTED_ENTRY_RING_DP = 2

internal fun dockDynamicSectionTileTestTag(label: String): String = "dock-dynamic-entry:$label"

internal const val DOCK_DYNAMIC_SECTION_TEST_TAG = "dock-dynamic-section"

/**
 * What a screen reader and a keyboard get on a dynamic entry: its description and selection, and for an app entry
 * "Show <app> in Stream" (the press that starts on the icon, in the Home mode) and Shift + Enter. ONE semantics
 * block, so one `customActions`
 * list: two would drop the earlier one. The Enter taken for Shift + Enter is taken through its release too, so it
 * never also opens the app.
 */
@Composable
private fun Modifier.dynamicEntryAccess(
    entry: DockDynamicEntry,
    isSelected: Boolean,
    onShowInStream: () -> Unit,
): Modifier {
    val showInStreamLabel = entry.showInStreamLabel
    val latestShow = rememberUpdatedState(onShowInStream)
    val enterTaken = remember { mutableStateOf(false) }
    return semantics {
        contentDescription = entry.contentDescription
        selected = isSelected
        if (showInStreamLabel != null) {
            customActions = listOf(CustomAccessibilityAction(showInStreamLabel) { latestShow.value().let { true } })
        }
    }.onPreviewKeyEvent { event ->
        val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
        when {
            showInStreamLabel == null || !enter -> false
            event.type == KeyEventType.KeyDown && event.isShiftPressed -> {
                enterTaken.value = true
                latestShow.value()
                true
            }
            event.type == KeyEventType.KeyUp && enterTaken.value -> {
                enterTaken.value = false
                true
            }
            else -> false
        }
    }
}

/** The long-press menu of an app entry: "Open in Stream", then the app's own shortcuts, App info and Hide. */
@Composable
private fun DynamicEntryMenu(
    entry: DockDynamicEntry,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShowInStream: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val latestShow = rememberUpdatedState(onShowInStream)
    val items =
        remember(entry.openInStreamMenuLabel, entry.appMenu) {
            listOfNotNull(
                entry.openInStreamMenuLabel?.let { label ->
                    ShortcutContextMenuItem(label = label, onSelect = { latestShow.value() })
                },
            ) + entry.appMenu
        }
    ShortcutContextMenu(expanded = expanded, items = items, onDismissRequest = onDismiss, onAction = onAction)
}
