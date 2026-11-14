package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HandleAxis
import com.riffle.core.domain.launcher.home.HomeHandle
import com.riffle.core.domain.launcher.home.HomeHandleLayout

/**
 * The Home handle (ADR 0003, Amendments 1 and 2): the search pill and the page indicator as one small pill just in
 * from the Dock, toward Home: above a bottom Dock, and in front of the middle of a side Dock (the caller places it).
 * It is drawn on Thin glass, [HomeHandle.PILL_HEIGHT_DP] thick, inside a [HomeHandle.TOUCH_TARGET_DP] target.
 *
 * - A tap anywhere on it opens search; a still long-press anywhere on it is [onLongPress] (Home's menu), given the
 *   press point in root coordinates. Null: a long-press does nothing.
 * - With more than one page it shows the page dots, and dragging along them scrubs between pages exactly as the page
 *   indicator always did ([PageIndicator]: the handle follows the finger, each page crossed is shown at once, release
 *   commits). With [dragEnabled] off the dots only show where you are; with [showsDots] off there are none.
 * - It runs along [axis], which is the way the pages scroll: the glyph, then the dots, along a row, or down a column
 *   for pages that scroll up and down. Its size is fixed by [HomeHandle.sizeDp], so the glyph and the dots keep the
 *   same room at every page count.
 * - For assistive technology it is two nodes: "Search" (a button) and the page selector, which says the page, can be
 *   set to a page and has "Previous page" and "Next page".
 *
 * The pointer inputs here are the platform's tap detector and a drag detector that consumes whatever drag starts on the
 * handle (along it, across it, on the glyph or on a single page's bare pill), so none of Home's swipes can fire from
 * it; the scrub along the dots is the indicator's own and takes the touch first, only once it moves past slop, so a tap
 * on the dots still searches.
 */
@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun HomeHandlePill(
    pageCount: Int,
    selectedPageIndex: Int,
    reducedMotion: Boolean,
    haptics: LauncherHaptics,
    onOpenSearch: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onLiveDragPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((Offset) -> Unit)? = null,
    axis: HandleAxis = HandleAxis.HORIZONTAL,
    showsDots: Boolean = true,
    dragEnabled: Boolean = true,
    /**
     * The Dock the handle stands in front of: the pill is drawn as the same material (its "Dock background" fill and
     * colour, 0% clear, reduced transparency, the same edge lights). Null: the plain Thin glass.
     */
    dock: DockModel? = null,
) {
    val dots = showsDots && HomeHandle.showsDots(pageCount)
    val searchLabel = stringResource(R.string.home_handle_search)
    val vertical = axis == HandleAxis.VERTICAL
    val size = HomeHandleLayout.sizeDp(axis, pageCount, dots)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestLongPress by rememberUpdatedState(onLongPress)
    val hasLongPress = onLongPress != null
    Box(
        modifier =
            modifier
                // Comes in with the Dock (Home's entrance): alpha and a rise from the Dock's edge, in the draw phase.
                .homeEntranceLayer(LocalHomeEntrance.current)
                .testTag(HOME_HANDLE_TEST_TAG)
                .size(width = size.widthDp.dp, height = size.heightDp.dp)
                .onGloballyPositioned { coordinates = it }
                .pointerInput(onOpenSearch, hasLongPress) {
                    detectTapGestures(
                        onTap = { onOpenSearch() },
                        onLongPress =
                            if (hasLongPress) {
                                { local ->
                                    val node = coordinates
                                    latestLongPress?.invoke(
                                        if (node != null && node.isAttached) node.localToRoot(local) else local,
                                    )
                                }
                            } else {
                                null
                            },
                    )
                }
                // Any drag that starts on the handle is the handle's (the dots' own scrub takes the one along them
                // first): it never reaches Home's swipes, which would turn a drag down the handle into a swipe down.
                .pointerInput(Unit) { detectDragGestures(onDrag = { change, _ -> change.consume() }) },
        contentAlignment = Alignment.Center,
    ) {
        // The pill is drawn thinner than its touch target: the glass sits in the middle of it.
        val inset = ((HomeHandle.TOUCH_TARGET_DP - HomeHandle.PILL_HEIGHT_DP) / 2).dp
        val pillModifier =
            Modifier.matchParentSize().padding(
                horizontal = if (vertical) inset else 0.dp,
                vertical = if (vertical) 0.dp else inset,
            )
        if (dock != null) {
            // The Dock's own material, by its own modifiers: the pill follows the Dock's fill setting and colour and
            // wears the same edge lights. Fully rounded unless the Dock's corner is tighter than the pill's half
            // height.
            val radius = dock.cornerRadiusDp.coerceIn(0, HomeHandle.PILL_HEIGHT_DP / 2)
            Box(pillModifier.dockSurfaceAppearance(dock, RoundedCornerShape(radius.dp)))
        } else {
            GlassSurface(
                modifier = pillModifier,
                weight = GlassWeight.Thin,
                shape = RoundedCornerShape(HomeHandle.PILL_HEIGHT_DP.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val content: @Composable () -> Unit = {
            HomeHandleGlyph(searchLabel = searchLabel, onOpenSearch = onOpenSearch)
            if (dots) {
                PageIndicator(
                    pageCount = pageCount,
                    selectedPageIndex = selectedPageIndex,
                    reducedMotion = reducedMotion,
                    haptics = haptics,
                    onPageSelected = onPageSelected,
                    onLiveDragPageChanged = onLiveDragPageChanged,
                    axis = axis,
                    dragEnabled = dragEnabled,
                )
                Spacer(
                    modifier =
                        if (vertical) {
                            Modifier.height(HomeHandle.TRAILING_PADDING_DP.dp)
                        } else {
                            Modifier.width(HomeHandle.TRAILING_PADDING_DP.dp)
                        },
                )
            }
        }
        if (vertical) {
            Column(
                modifier = Modifier.fillMaxHeight().width(HomeHandle.TOUCH_TARGET_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(HomeHandle.TOUCH_TARGET_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { content() }
        }
    }
}

/** The search glyph in its 48 dp square: the handle's "Search" button for assistive technology. */
@Composable
private fun HomeHandleGlyph(
    searchLabel: String,
    onOpenSearch: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(HomeHandle.TOUCH_TARGET_DP.dp)
                .semantics {
                    contentDescription = searchLabel
                    role = Role.Button
                    onClick(label = searchLabel) {
                        onOpenSearch()
                        true
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(HomeHandle.SEARCH_GLYPH_DP.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal const val HOME_HANDLE_TEST_TAG = "home-handle"
