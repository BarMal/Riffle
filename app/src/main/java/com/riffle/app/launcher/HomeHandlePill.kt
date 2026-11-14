package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import com.riffle.core.domain.launcher.home.HomeHandle

/**
 * The Home handle (ADR 0003, Amendment 1): the search pill and the page indicator as one small pill just above the
 * Dock, toward Home. It is drawn on Thin glass at [HomeHandle.PILL_HEIGHT_DP], inside a [HomeHandle.TOUCH_TARGET_DP]
 * target.
 *
 * - A tap anywhere on it opens search.
 * - With more than one page it shows the page dots, and dragging along them scrubs between pages exactly as the page
 *   indicator always did ([PageIndicator]: the handle follows the finger, each page crossed is shown at once, release
 *   commits).
 * - For assistive technology it is two nodes: "Search" (a button) and the page selector, which says the page, can be
 *   set to a page and has "Previous page" and "Next page".
 *
 * The one pointer input here is the platform's tap detector; the drag is the indicator's own and takes the touch only
 * once it moves past slop, so a tap on the dots still searches.
 */
@Composable
@Suppress("LongParameterList")
internal fun HomeHandlePill(
    pageCount: Int,
    selectedPageIndex: Int,
    reducedMotion: Boolean,
    haptics: LauncherHaptics,
    onOpenSearch: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onLiveDragPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showsDots = HomeHandle.showsDots(pageCount)
    val searchLabel = stringResource(R.string.home_handle_search)
    val trailing = HomeHandle.TRAILING_PADDING_DP.dp
    Box(
        modifier =
            modifier
                .testTag(HOME_HANDLE_TEST_TAG)
                .heightIn(min = HomeHandle.TOUCH_TARGET_DP.dp)
                .widthIn(min = HomeHandle.TOUCH_TARGET_DP.dp)
                .pointerInput(onOpenSearch) { detectTapGestures(onTap = { onOpenSearch() }) },
        contentAlignment = Alignment.Center,
    ) {
        // The pill is drawn shorter than its touch target: the glass sits in the middle of it.
        val inset = ((HomeHandle.TOUCH_TARGET_DP - HomeHandle.PILL_HEIGHT_DP) / 2).dp
        GlassSurface(
            modifier = Modifier.matchParentSize().padding(vertical = inset),
            weight = GlassWeight.Thin,
            shape = RoundedCornerShape(HomeHandle.PILL_HEIGHT_DP.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.height(HomeHandle.TOUCH_TARGET_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
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
            if (showsDots) {
                PageIndicator(
                    pageCount = pageCount,
                    selectedPageIndex = selectedPageIndex,
                    reducedMotion = reducedMotion,
                    haptics = haptics,
                    onPageSelected = onPageSelected,
                    onLiveDragPageChanged = onLiveDragPageChanged,
                )
                Spacer(modifier = Modifier.width(trailing))
            }
        }
    }
}

internal const val HOME_HANDLE_TEST_TAG = "home-handle"
